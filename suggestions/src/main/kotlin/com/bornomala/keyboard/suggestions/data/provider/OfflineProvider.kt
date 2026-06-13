package com.bornomala.keyboard.suggestions.data.provider

import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.core.result.getOrDefault
import com.bornomala.keyboard.suggestions.data.dictionary.BigramDictionaryRepository
import com.bornomala.keyboard.suggestions.data.dictionary.DictionaryHit
import com.bornomala.keyboard.suggestions.data.dictionary.FrequencyDictionaryRepository
import com.bornomala.keyboard.suggestions.data.dictionary.OffensiveWordRepository
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
    private val bigrams: BigramDictionaryRepository,
    private val userDictionary: UserDictionaryRepository,
    private val offensiveWords: OffensiveWordRepository,
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
        val prev = normalize(request.previousWord, lang)
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

        // 4) Apostrophe contraction: "wont" -> "won't", "im" -> "i'm", etc. Offered with a high
        //    score so it leads (the adapter highlights the top non-exact suggestion), while the
        //    verbatim form remains available. Only for English and only on a complete-word match.
        if (lang == SuggestionLanguage.ENGLISH) {
            CONTRACTIONS[prefix]?.let { contraction ->
                putBest(merged, Suggestion(
                    word = contraction,
                    language = lang,
                    source = SuggestionSource.OFFLINE_DICTIONARY,
                    score = CONTRACTION_SCORE,
                ))
            }
        }

        // 5) Spelling corrections (English): QWERTY-aware edit-distance-1 candidates that are
        //    real words. Offered as alternates; the best one is flagged for auto-correct only
        //    when the typed word is not itself a known word (a likely finished typo).
        var autoCorrectWord: String? = null
        if (lang == SuggestionLanguage.ENGLISH && prefix.length >= 2) {
            // A word counts as "known" (so it is not auto-corrected) when it is in the bundled
            // dictionary, is a contraction, or the user has typed it at least LEARN_TRUST times.
            // The frequency gate matters: a single accidental commit of a typo (e.g. before
            // auto-correct kicked in) must not permanently mark that typo as a real word.
            val verbatimKnown = dict.frequencyOf(prefix) > 0 ||
                CONTRACTIONS.containsKey(prefix) ||
                userHits.any { normalize(it.word, lang) == prefix && it.frequency >= LEARN_TRUST }
            val corrections = FuzzyCorrector.corrections(prefix, dict, fetch)
            for (c in corrections) {
                val score = (c.frequency.toDouble() / max) * c.editScore * CORRECTION_WEIGHT
                putBest(merged, Suggestion(
                    word = c.word,
                    language = lang,
                    source = SuggestionSource.CORRECTION,
                    score = score,
                ))
            }
            val startsUpper = request.currentWord.firstOrNull()?.isUpperCase() == true
            if (!verbatimKnown && !startsUpper && prefix.length >= MIN_AUTOCORRECT_LEN && corrections.isNotEmpty()) {
                autoCorrectWord = corrections.first().word
            }
        }

        // 6) Context re-rank (English): lift completions of the current prefix that are likely
        //    to follow the previous word (bigram next-words of `prev`), so e.g. "how" + "t…"
        //    surfaces "to"/"the" ahead of equally-spelled but contextually unlikely words.
        if (lang == SuggestionLanguage.ENGLISH && prev.isNotEmpty() && prefix.isNotEmpty()) {
            val ctx = bigrams.get(lang).nextWords(prev, CONTEXT_LOOKUP)
            ctx.forEachIndexed { idx, w ->
                val existing = merged[w] ?: return@forEachIndexed
                val boost = CONTEXT_BOOST * (1.0 - idx * CONTEXT_STEP).coerceAtLeast(0.0)
                merged[w] = existing.copy(score = (existing.score + boost).coerceAtMost(MAX_SCORE))
            }
        }

        // Offensive filter: drop profanity/slurs from candidates and corrections, but keep the
        // exact word the user actually typed (they can always commit what they wrote).
        if (request.blockOffensive) {
            val blocked = offensiveWords.get(lang)
            if (blocked.isNotEmpty()) {
                val iterator = merged.entries.iterator()
                while (iterator.hasNext()) {
                    val word = normalize(iterator.next().key, lang)
                    if (word != prefix && word in blocked) iterator.remove()
                }
                if (autoCorrectWord != null && normalize(autoCorrectWord, lang) in blocked) {
                    autoCorrectWord = null
                }
            }
        }

        var ranked = merged.values
            .sortedWith(RANK_COMPARATOR)
            .take(limit)
        // Flag the auto-correct target only if it actually leads the ranking, so a strong
        // completion of a partially-typed word is never silently replaced.
        if (autoCorrectWord != null &&
            ranked.firstOrNull()?.word == autoCorrectWord &&
            ranked.first().source == SuggestionSource.CORRECTION
        ) {
            ranked = ranked.mapIndexed { i, s -> if (i == 0) s.copy(autoCorrect = true) else s }
        }
        return ranked
    }

    private suspend fun nextWordSuggestions(request: SuggestionRequest): List<Suggestion> {
        val lang = request.language
        val prev = normalize(request.previousWord, lang)
        val limit = request.limit
        val fetch = limit + EXTRA_FETCH
        val merged = LinkedHashMap<String, Suggestion>(fetch * 2)

        val prev2 = normalize(request.secondPreviousWord, lang)

        if (prev.isNotEmpty()) {
            // 1) Learned TRIGRAM — what THIS user typed after `prev2 prev`. Strongest (most
            //    specific context), boosted above the bigram tier.
            if (prev2.isNotEmpty()) {
                val tri = userDictionary.queryNgram(lang, "$prev2 $prev", fetch).getOrDefault(emptyList())
                for (entry in tri) {
                    putBest(merged, Suggestion(
                        word = entry.word,
                        language = lang,
                        source = SuggestionSource.USER_DICTIONARY,
                        score = (normalizeUserFrequency(entry.frequency) + USER_BOOST + TRIGRAM_BONUS)
                            .coerceAtMost(MAX_SCORE),
                    ))
                }
            }
            // 2) Learned BIGRAM — words the user typed after `prev`.
            val learned = userDictionary.queryNgram(lang, prev, fetch).getOrDefault(emptyList())
            for (entry in learned) {
                putBest(merged, Suggestion(
                    word = entry.word,
                    language = lang,
                    source = SuggestionSource.USER_DICTIONARY,
                    score = (normalizeUserFrequency(entry.frequency) + USER_BOOST).coerceAtMost(MAX_SCORE),
                ))
            }
            // 3) Bundled bigram seed — common continuations of `prev`.
            bigrams.get(lang).nextWords(prev, fetch).forEachIndexed { i, w ->
                if (w != prev) {
                    putBest(merged, Suggestion(
                        word = w,
                        language = lang,
                        source = SuggestionSource.NEXT_WORD,
                        score = SEED_BASE - i * SEED_STEP,
                    ))
                }
            }
        }

        // 3) Generic fallback: the most frequent words overall, so the strip is never empty
        //    (e.g. right after a space with no learned/seed context). Lowest priority.
        if (merged.size < limit) {
            dictionaries.get(lang).topWords(fetch).forEachIndexed { i, hit ->
                if (hit.word != prev && !merged.containsKey(hit.word)) {
                    putBest(merged, Suggestion(
                        word = hit.word,
                        language = lang,
                        source = SuggestionSource.NEXT_WORD,
                        score = GENERIC_BASE - i * GENERIC_STEP,
                    ))
                }
            }
        }

        // Never predict an offensive next word when the filter is on.
        val blocked = if (request.blockOffensive) offensiveWords.get(lang) else emptySet()
        return merged.values.asSequence()
            .filter { blocked.isEmpty() || normalize(it.word, lang) !in blocked }
            .sortedWith(RANK_COMPARATOR)
            .take(limit)
            .toList()
    }

    override suspend fun learn(
        word: String,
        previousWord: String,
        secondPreviousWord: String,
        language: SuggestionLanguage,
    ): AppResult<Unit> {
        val normalized = normalize(word, language)
        if (normalized.isEmpty()) {
            return AppResult.Failure(
                com.bornomala.keyboard.core.result.AppError.Validation("Cannot learn empty word"),
            )
        }
        val prev = normalize(previousWord, language)
        val prev2 = normalize(secondPreviousWord, language)
        // The word row backs prefix-completion + recency.
        val result = userDictionary.learn(word = normalized, previousWord = prev, language = language)
        // Learned n-grams back next-word prediction: bigram (prev) and trigram (prev2 prev).
        if (prev.isNotEmpty()) {
            userDictionary.learnNgram(language, prev, normalized)
            if (prev2.isNotEmpty()) {
                userDictionary.learnNgram(language, "$prev2 $prev", normalized)
            }
        }
        return result
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

        /** Extra boost for a trigram match so it leads its bigram counterpart. */
        private const val TRIGRAM_BONUS = 0.1
        private const val MAX_SCORE = 2.0
        private const val VERBATIM_SCORE = 0.001
        private const val EXTRA_FETCH = 2
        private const val FREQ_SATURATION = 50.0
        private val FREQ_SATURATION_LOG = Math.log(FREQ_SATURATION + 1)

        // Next-word scoring tiers (learned bigrams already outrank these via USER_BOOST).
        private const val SEED_BASE = 0.70
        private const val SEED_STEP = 0.02
        private const val GENERIC_BASE = 0.20
        private const val GENERIC_STEP = 0.005

        /** Score for an apostrophe contraction — above any plain dictionary word (<= 1.0). */
        private const val CONTRACTION_SCORE = 1.5

        /** Scales a correction's (frequency × edit-confidence) so it sits just under exact hits. */
        private const val CORRECTION_WEIGHT = 0.95

        /** Shortest typed word eligible for silent auto-correct on space (shorter = likely mid-word). */
        private const val MIN_AUTOCORRECT_LEN = 3

        /** Times a word must be learned before it suppresses auto-correct (blocks one-off typos). */
        private const val LEARN_TRUST = 2

        // Previous-word context boost applied to current-word completions (Gboard-style re-rank).
        private const val CONTEXT_LOOKUP = 12
        private const val CONTEXT_BOOST = 0.5
        private const val CONTEXT_STEP = 0.05

        /**
         * No-apostrophe -> apostrophe contractions, keyed by the lower-cased typed word.
         * Offered as a tap-only suggestion (space never auto-applies it), so even forms that
         * are also ordinary words ("its", "were", "ill") are safe to include — they only take
         * effect if the user taps the chip. First-person forms are capitalised (I'm, I'll, …).
         */
        private val CONTRACTIONS: Map<String, String> = mapOf(
            // be / will / would / have / had  (am/are/is)
            "im" to "I'm", "youre" to "you're", "hes" to "he's", "shes" to "she's",
            "its" to "it's", "were" to "we're", "theyre" to "they're", "thats" to "that's",
            "whats" to "what's", "whos" to "who's", "wheres" to "where's", "whens" to "when's",
            "whys" to "why's", "hows" to "how's", "heres" to "here's", "theres" to "there's",
            "lets" to "let's",
            // 'll (will)
            "ill" to "I'll", "youll" to "you'll", "hell" to "he'll", "shell" to "she'll",
            "well" to "we'll", "theyll" to "they'll", "itll" to "it'll", "thatll" to "that'll",
            "thisll" to "this'll", "wholl" to "who'll", "whatll" to "what'll", "therell" to "there'll",
            // 'd (would / had)
            "id" to "I'd", "youd" to "you'd", "hed" to "he'd", "shed" to "she'd",
            "wed" to "we'd", "theyd" to "they'd", "itd" to "it'd", "thatd" to "that'd",
            "whod" to "who'd", "howd" to "how'd", "whered" to "where'd",
            // 've (have)
            "ive" to "I've", "youve" to "you've", "weve" to "we've", "theyve" to "they've",
            "couldve" to "could've", "wouldve" to "would've", "shouldve" to "should've",
            "mightve" to "might've", "mustve" to "must've",
            // n't (not)
            "wont" to "won't", "dont" to "don't", "cant" to "can't", "didnt" to "didn't",
            "doesnt" to "doesn't", "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't",
            "werent" to "weren't", "hasnt" to "hasn't", "havent" to "haven't", "hadnt" to "hadn't",
            "wouldnt" to "wouldn't", "couldnt" to "couldn't", "shouldnt" to "shouldn't",
            "mustnt" to "mustn't", "neednt" to "needn't", "mightnt" to "mightn't",
            "shant" to "shan't", "oughtnt" to "oughtn't", "aint" to "ain't",
            // misc
            "yall" to "y'all", "oclock" to "o'clock", "maam" to "ma'am", "cmon" to "c'mon",
        )

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
