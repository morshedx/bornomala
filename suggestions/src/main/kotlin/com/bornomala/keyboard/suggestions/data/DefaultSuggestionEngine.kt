package com.bornomala.keyboard.suggestions.data

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.core.result.getOrDefault
import com.bornomala.keyboard.suggestions.data.dictionary.BanglaPhoneticKey
import com.bornomala.keyboard.suggestions.data.dictionary.BanglaPhoneticRepository
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository
import com.bornomala.keyboard.suggestions.domain.SuggestionEngine
import com.bornomala.keyboard.suggestions.domain.SuggestionProvider
import com.bornomala.keyboard.suggestions.domain.model.Suggestion
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.domain.model.SuggestionRequest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SuggestionEngine].
 *
 * Fans a request out to every registered [SuggestionProvider] that reports
 * [SuggestionProvider.isAvailable], merges their results (keeping the highest score per
 * word, breaking ties by provider priority), ranks, and returns the top
 * [SuggestionRequest.limit].
 *
 * Provider selection is the load-bearing privacy guarantee: only available providers
 * are queried, so the inert [com.bornomala.keyboard.suggestions.data.provider.FutureCloudProvider]
 * (which reports unavailable) is skipped without any network attempt — and in V1 it is
 * not even in the injected set.
 *
 * Ranking work runs on [DispatcherProvider.default]; providers do their own I/O
 * dispatching internally. The engine never throws: a provider failure is dropped.
 */
@Singleton
class DefaultSuggestionEngine @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards SuggestionProvider>,
    private val dispatchers: DispatcherProvider,
    private val banglaPhonetic: BanglaPhoneticRepository,
    private val userDictionary: UserDictionaryRepository,
) : SuggestionEngine {

    /**
     * Resolves roman Avro input against both the bundled phonetic index and the words the user
     * has taught the keyboard, which share one ambiguity-collapsed key space.
     *
     * Learned words the user has committed at least [LEARNED_TRUST] times lead — that is what
     * makes "learn from my typing" actually steer the auto-pick, instead of the bundled corpus
     * winning forever. A word seen only once trails the bundled hits: one accidental commit
     * should not promote itself to the word space silently swaps in.
     */
    override suspend fun banglaPhoneticCandidates(roman: String, limit: Int): List<String> {
        if (roman.isBlank() || limit <= 0) return emptyList()
        val key = BanglaPhoneticKey.romanKey(roman)
        if (key.isEmpty()) return emptyList()
        userDictionary.backfillPhoneticKeys(SuggestionLanguage.BANGLA)
        val learned = userDictionary
            .queryByPhoneticKey(SuggestionLanguage.BANGLA, key, limit)
            .getOrDefault(emptyList())
        val bundled = banglaPhonetic.candidatesForKey(key, limit)
        if (learned.isEmpty()) return bundled

        val out = ArrayList<String>(limit)
        for (entry in learned) {
            if (entry.frequency < LEARNED_TRUST) continue
            if (out.size >= limit) return out
            if (!out.contains(entry.word)) out.add(entry.word)
        }
        for (word in bundled) {
            if (out.size >= limit) return out
            if (!out.contains(word)) out.add(word)
        }
        for (entry in learned) {
            if (out.size >= limit) return out
            if (!out.contains(entry.word)) out.add(entry.word)
        }
        return out
    }

    override suspend fun getSuggestions(request: SuggestionRequest): List<Suggestion> {
        val active = providers
            .asSequence()
            .filter { it.isAvailable(request.language) }
            .sortedByDescending { it.priority }
            .toList()
        if (active.isEmpty()) return emptyList()

        return withContext(dispatchers.default) {
            // Highest-priority providers are queried first so their results win ties
            // during the merge. Sequential rather than parallel because the offline
            // provider is the only one in V1 and parallel fan-out would add coroutine
            // overhead to the hot path for no benefit.
            val merged = LinkedHashMap<String, Suggestion>(request.limit * 2)
            for (provider in active) {
                val result = provider.suggest(request)
                if (result is AppResult.Success) {
                    for (suggestion in result.data) {
                        mergeKeepingBest(merged, suggestion, provider.priority)
                    }
                }
                // Failure: skip this provider, keep whatever others produced.
            }
            merged.values
                .sortedWith(RANK_COMPARATOR)
                .take(request.limit)
        }
    }

    override suspend fun onWordCommitted(
        word: String,
        previousWord: String,
        secondPreviousWord: String,
        language: SuggestionLanguage,
    ) {
        if (word.isBlank()) return
        // Learn into every available provider. Failures are swallowed so a learning
        // hiccup never interrupts typing.
        for (provider in providers) {
            if (!provider.isAvailable(language)) continue
            provider.learn(word, previousWord, secondPreviousWord, language)
        }
    }

    /**
     * Inserts [candidate] unless an equal-or-better entry for the same word exists.
     * Ties on score are decided by [providerPriority] (higher wins).
     */
    private fun mergeKeepingBest(
        map: LinkedHashMap<String, Suggestion>,
        candidate: Suggestion,
        providerPriority: Int,
    ) {
        val existing = map[candidate.word]
        if (existing == null) {
            map[candidate.word] = candidate
            return
        }
        // Existing was inserted by an equal/higher priority provider first (active is
        // priority-sorted), so only replace when the new score is strictly higher.
        if (candidate.score > existing.score) {
            map[candidate.word] = candidate
        }
    }

    private companion object {
        /** Commits of a learned Bangla word before it outranks the bundled phonetic index. */
        const val LEARNED_TRUST = 2

        val RANK_COMPARATOR: Comparator<Suggestion> =
            compareByDescending<Suggestion> { it.score }
                .thenBy { it.source.ordinal }
                .thenBy { it.word }
    }
}
