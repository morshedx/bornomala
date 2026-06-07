package com.bornomala.keyboard.suggestions.data.provider

import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.core.result.getOrDefault
import com.bornomala.keyboard.suggestions.data.dictionary.DictionaryHit
import com.bornomala.keyboard.suggestions.data.dictionary.FrequencyDictionaryRepository
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository
import com.bornomala.keyboard.suggestions.domain.SuggestionProvider
import com.bornomala.keyboard.suggestions.domain.model.Suggestion
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.domain.model.SuggestionRequest
import com.bornomala.keyboard.suggestions.domain.model.SuggestionSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only active V1 provider: fully offline, backed by bundled English/Bangla
 * frequency dictionaries plus the on-device Room user dictionary.
 *
 * Ranking model (all normalized into [0,1] so the engine can merge across providers):
 * - User-dictionary hits get [USER_BOOST] added on top of their normalized frequency,
 *   so words the user actually types outrank generic dictionary entries.
 * - Bundled-dictionary hits score by frequency / maxFrequency.
 * - For next-word-only requests (cursor right after a space) only the user dictionary
 *   can predict, since the bundled frequency lists are unigram-only in V1; the bigram
 *   seam lives in [UserDictionaryRepository.queryNextWord].
 * - A verbatim suggestion of the exact current input is appended (low score) so the
 *   user can always keep what they typed, even for unknown words.
 *
 * No allocations beyond the result lists; dictionary prefix scanning collects into a
 * reused buffer. Never throws across the [SuggestionProvider] boundary.
 */
@Singleton
class OfflineProvider @Inject constructor(
    private val dictionaries: FrequencyDictionaryRepository,
    private val userDictionary: UserDictionaryRepository,
) : SuggestionProvider {

    override val id: String = ID
    override val priority: Int = SuggestionProvider.PRIORITY_OFFLINE

    /** Always available: it is purely on-device and needs no network. */
    override fun isAvailable(language: SuggestionLanguage): Boolean = true

    override suspend fun suggest(request: SuggestionRequest): AppResult<List<Suggestion>> {
        return try {
            val results = if (request.isNextWordOnly) {
                nextWordSuggestions(request)
            } else {
                currentWordSuggestions(request)
            }
            AppResult.Success(results)
        } catch (t: Throwable) {
            // Surface as a typed failure so the engine can drop us and keep going,
            // rather than failing the whole keystroke. CancellationException is
            // rethrown by AppError.from inside the conversion below.
            AppResult.Failure(com.bornomala.keyboard.core.result.AppError.from(t))
        }
    }

    private suspend fun currentWordSuggestions(request: SuggestionRequest): List<Suggestion> {
        val lang = request.language
        val prefix = normalize(request.currentWord, lang)
        val limit = request.limit
        // Over-fetch a little from each source so the merge has room to rank well.
        val fetch = limit + EXTRA_FETCH

        val merged = LinkedHashMap<String, Suggestion>(fetch * 2)

        // 1) User dictionary (boosted).
        val userHits = userDictionary
            .queryByPrefix(lang, prefix, fetch)
            .getOrDefault(emptyList())
        for (entry in userHits) {
            val base = normalizeUserFrequency(entry.frequency)
            putBest(merged, Suggestion(
                word = entry.word,
                language = lang,
                source = SuggestionSource.USER_DICTIONARY,
                score = (base + USER_BOOST).coerceAtMost(MAX_SCORE),
            ))
        }

        // 2) Bundled frequency dictionary.
        val dict = dictionaries.get(lang)
        val buffer = ArrayList<DictionaryHit>(fetch)
        dict.collectByPrefix(prefix, fetch, buffer)
        val max = dict.maxFrequency().coerceAtLeast(1)
        for (hit in buffer) {
            val score = hit.frequency.toDouble() / max
            putBest(merged, Suggestion(
                word = hit.word,
                language = lang,
                source = SuggestionSource.OFFLINE_DICTIONARY,
                score = score,
            ))
        }

        // 3) Verbatim fallback: ensure the exact input is always offerable.
        val verbatim = request.currentWord
        if (verbatim.isNotEmpty() && !merged.containsKey(verbatim)) {
            putBest(merged, Suggestion(
                word = verbatim,
                language = lang,
                source = SuggestionSource.VERBATIM,
                score = VERBATIM_SCORE,
                isExactMatch = true,
            ))
        } else if (verbatim.isNotEmpty()) {
            // Mark the existing exact match so the UI can highlight it.
            merged[verbatim]?.let { existing ->
                merged[verbatim] = existing.copy(isExactMatch = true)
            }
        }

        return merged.values
            .sortedWith(RANK_COMPARATOR)
            .take(limit)
    }

    private suspend fun nextWordSuggestions(request: SuggestionRequest): List<Suggestion> {
        val lang = request.language
        val prev = normalize(request.previousWord, lang)
        if (prev.isEmpty()) return emptyList()

        val userHits = userDictionary
            .queryNextWord(lang, prev, request.limit)
            .getOrDefault(emptyList())

        return userHits.map { entry ->
            Suggestion(
                word = entry.word,
                language = lang,
                source = SuggestionSource.USER_DICTIONARY,
                score = (normalizeUserFrequency(entry.frequency) + USER_BOOST).coerceAtMost(MAX_SCORE),
            )
        }.sortedWith(RANK_COMPARATOR).take(request.limit)
    }

    override suspend fun learn(
        word: String,
        previousWord: String,
        language: SuggestionLanguage,
    ): AppResult<Unit> {
        val normalized = normalize(word, language)
        if (normalized.isEmpty()) {
            return AppResult.Failure(
                com.bornomala.keyboard.core.result.AppError.Validation("Cannot learn empty word"),
            )
        }
        return userDictionary.learn(
            word = normalized,
            previousWord = normalize(previousWord, language),
            language = language,
        )
    }

    /** Keeps the higher-scored suggestion when the same word arrives from two sources. */
    private fun putBest(map: LinkedHashMap<String, Suggestion>, candidate: Suggestion) {
        val existing = map[candidate.word]
        if (existing == null || candidate.score > existing.score) {
            map[candidate.word] = candidate
        }
    }

    private fun normalize(word: String, language: SuggestionLanguage): String =
        when (language) {
            SuggestionLanguage.ENGLISH -> word.trim().lowercase()
            SuggestionLanguage.BANGLA -> word.trim()
        }

    /**
     * Maps a raw learned-frequency count into [0,1] with diminishing returns, so a
     * single very frequent learned word cannot dominate forever. log-based curve.
     */
    private fun normalizeUserFrequency(frequency: Int): Double {
        if (frequency <= 0) return 0.0
        // ln(f+1)/ln(FREQ_SATURATION+1), capped at 1.
        val ratio = Math.log((frequency + 1).toDouble()) / FREQ_SATURATION_LOG
        return ratio.coerceIn(0.0, 1.0)
    }

    companion object {
        const val ID = "offline"

        /** Additive boost so any user-learned word outranks generic dictionary words. */
        private const val USER_BOOST = 1.0
        private const val MAX_SCORE = 2.0
        private const val VERBATIM_SCORE = 0.001
        private const val EXTRA_FETCH = 2
        private const val FREQ_SATURATION = 50.0
        private val FREQ_SATURATION_LOG = Math.log(FREQ_SATURATION + 1)

        /**
         * Ranking: higher score first; on ties prefer user dictionary, then offline
         * dictionary, then verbatim; final tie-break alphabetical for determinism.
         */
        private val RANK_COMPARATOR: Comparator<Suggestion> =
            compareByDescending<Suggestion> { it.score }
                .thenBy { it.source.ordinal }
                .thenBy { it.word }
    }
}
