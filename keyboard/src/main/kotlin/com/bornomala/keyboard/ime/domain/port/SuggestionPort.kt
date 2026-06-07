package com.bornomala.keyboard.ime.domain.port

import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.Suggestion

/**
 * Inbound port the keyboard uses to obtain word candidates and to report committed words
 * for learning. Bound by the app to an adapter over the :suggestions module's
 * `SuggestionEngine` / `SuggestionProvider`. Defining it here inverts the dependency so
 * :keyboard does not compile-time depend on :suggestions.
 *
 * [query] is `suspend` because dictionary lookups run off the main thread (on
 * `DispatcherProvider.default`); the keyboard launches it from its service scope and
 * never blocks input on it. [recordCommitted] is fire-and-forget (the implementation
 * coalesces/persists asynchronously) and must return immediately.
 */
interface SuggestionPort {

    /**
     * Returns ranked candidates for the given context.
     *
     * @param language active language (selects the dictionary).
     * @param currentWord the word being typed (may be empty for pure next-word prediction).
     * @param previousWord the word immediately before the cursor, for next-word prediction;
     *   empty if there is none.
     * @param limit maximum candidates to return.
     */
    suspend fun query(
        language: KeyboardLanguage,
        currentWord: String,
        previousWord: String,
        limit: Int,
    ): List<Suggestion>

    /**
     * Records that the user committed [word] in [language] so the engine can learn
     * frequency / next-word transitions. Must not block; persistence is debounced by the
     * implementation.
     */
    fun recordCommitted(language: KeyboardLanguage, word: String)
}
