package com.bornomala.keyboard.suggestions.domain.model

/**
 * The immutable context a provider needs to produce suggestions for one keystroke.
 *
 * Reused across the keystroke hot path; callers should build it cheaply (no heavy
 * allocations). [currentWord] is the partially typed token under the cursor;
 * [previousWord] is the last fully committed token to the left, used for next-word
 * prediction (it is empty at the start of a sentence).
 *
 * @property currentWord the in-progress token (may be empty when requesting pure
 *   next-word predictions after a space).
 * @property previousWord the committed token immediately before [currentWord]; empty
 *   if none. Used for bigram / next-word ranking.
 * @property language which language dictionaries to consult.
 * @property limit maximum number of suggestions to return. Must be positive.
 */
data class SuggestionRequest(
    val currentWord: String,
    val previousWord: String,
    val language: SuggestionLanguage,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(limit > 0) { "limit must be positive, was $limit" }
    }

    /** True when there is no partial word, i.e. the caller wants next-word predictions only. */
    val isNextWordOnly: Boolean get() = currentWord.isEmpty()

    companion object {
        const val DEFAULT_LIMIT: Int = 3
    }
}
