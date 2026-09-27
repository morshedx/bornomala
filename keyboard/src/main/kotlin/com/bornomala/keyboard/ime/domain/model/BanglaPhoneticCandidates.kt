package com.bornomala.keyboard.ime.domain.model

/**
 * Bangla words for a roman input, best-first. Only the first [trustedCount] may be auto-picked on
 * space; the rest (dictionary-only words, suffix joins) are offered on the strip and count as
 * real words, but are never silently swapped in for what the user typed.
 *
 * @param learnedOnly the [words] present only because the keyboard learned them from the user's
 *   typing, not from any bundled dictionary.
 */
data class BanglaPhoneticCandidates(
    val words: List<String>,
    val trustedCount: Int,
    val learnedOnly: Set<String> = emptySet(),
) {

    /** The best auto-pickable word, or null. */
    val topTrusted: String? get() = if (trustedCount > 0) words.firstOrNull() else null

    companion object {
        val EMPTY = BanglaPhoneticCandidates(emptyList(), 0)
    }
}
