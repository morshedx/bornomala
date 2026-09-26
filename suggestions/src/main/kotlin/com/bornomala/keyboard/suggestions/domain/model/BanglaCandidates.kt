package com.bornomala.keyboard.suggestions.domain.model

/**
 * Bangla words for a roman input, best-first.
 *
 * @param words every candidate, de-duplicated.
 * @param trustedCount how many leading [words] may be auto-picked on space: learned and corpus
 *   words. The rest (riti dictionary words, suffix-joined forms) are real words worth offering,
 *   but are never silently swapped in for what the user typed.
 */
data class BanglaCandidates(val words: List<String>, val trustedCount: Int) {
    companion object {
        val EMPTY = BanglaCandidates(emptyList(), 0)
    }
}
