package com.bornomala.keyboard.suggestions.domain

import com.bornomala.keyboard.suggestions.domain.model.Suggestion
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.domain.model.SuggestionRequest

/**
 * The single entry point the keyboard/IME layer talks to for suggestions.
 *
 * The engine fans a [SuggestionRequest] out to every registered [SuggestionProvider]
 * that reports [SuggestionProvider.isAvailable], merges and de-duplicates their
 * candidates, ranks them, and returns the top [SuggestionRequest.limit]. It also
 * forwards learning signals to providers on word commit.
 *
 * All methods are `suspend` and must be invoked off the main thread by the caller's
 * coroutine. The engine itself performs no main-thread work and allocates lazily.
 */
interface SuggestionEngine {

    /**
     * Returns ranked suggestions for [request]. Never throws; on total provider
     * failure it returns an empty list (the keyboard simply shows no suggestions).
     */
    suspend fun getSuggestions(request: SuggestionRequest): List<Suggestion>

    /**
     * Notifies the engine that the user committed [word] (e.g. tapped space, picked
     * a suggestion, or finished a word). Propagated to learning-capable providers so
     * frequency and next-word statistics improve over time. Fire-and-forget from the
     * caller's perspective; errors are swallowed so input is never interrupted.
     *
     * @param previousWord the committed token before [word]; empty if none.
     */
    suspend fun onWordCommitted(
        word: String,
        previousWord: String,
        secondPreviousWord: String,
        language: SuggestionLanguage,
    )
}
