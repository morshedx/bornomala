package com.bornomala.keyboard.ime.domain.port

import com.bornomala.keyboard.ime.domain.model.BanglaPhoneticCandidates
import com.bornomala.keyboard.ime.domain.model.BanglaWordMatch
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
        secondPreviousWord: String,
        limit: Int,
        blockOffensive: Boolean = false,
    ): List<Suggestion>

    /**
     * Resolves a roman (Avro-style) Bangla input to real Bangla words via the bundled phonetic
     * index — e.g. `chara` -> [ছাড়া, ছাড়াও] — ranked by frequency, then suggest-only words
     * (see [BanglaPhoneticCandidates.trustedCount]). Empty for other languages or when nothing
     * matches. Runs off the main thread like [query].
     */
    suspend fun banglaPhonetic(roman: String, limit: Int): BanglaPhoneticCandidates =
        BanglaPhoneticCandidates.EMPTY

    /**
     * Resolves the exact roman spelling [roman] to a whole Bangla word the phonetic index cannot
     * reach — an English loanword (`chair` -> চেয়ার) or the user's own earlier pick for that
     * spelling. Null when there is none. Runs off the main thread like [query].
     */
    suspend fun banglaWord(roman: String): BanglaWordMatch? = null

    /**
     * Records that the user picked [word] from the strip for the roman input [roman], so it is
     * auto-picked for that spelling next time. Fire-and-forget; must not block.
     */
    fun recordBanglaPick(roman: String, word: String) = Unit

    /**
     * Records that the user committed [word] in [language] so the engine can learn
     * frequency / next-word transitions. Must not block; persistence is debounced by the
     * implementation.
     */
    fun recordCommitted(language: KeyboardLanguage, word: String)
}
