package com.bornomala.keyboard.suggestions.data.provider

import com.bornomala.keyboard.suggestions.data.dictionary.FrequencyDictionary

/**
 * Generates spelling corrections for a typed word by enumerating edit-distance-1 variants and
 * keeping those that are real dictionary words. Edits are QWERTY-adjacency aware: substitutions
 * and insertions only use keys physically next to the typed key (plus letter doubling), since
 * the overwhelming majority of typos are neighbour-key slips, missing/extra letters, or
 * adjacent transpositions. This keeps the candidate set small (≈ a few dozen lookups, each an
 * O(log n) binary search) so it stays within the per-keystroke budget — no full-dictionary scan.
 *
 * Pure and allocation-bounded; produces at most one [Candidate] per distinct corrected word
 * (the highest-scoring edit for it).
 */
internal object FuzzyCorrector {

    /** A correction candidate: the dictionary [word], its raw [frequency], and an [editScore]. */
    data class Candidate(val word: String, val frequency: Int, val editScore: Double)

    /** Per-key QWERTY neighbours (the keys a finger is most likely to hit by mistake). */
    private val NEIGHBORS: Map<Char, String> = mapOf(
        'q' to "wa", 'w' to "qeas", 'e' to "wrsd", 'r' to "etdf", 't' to "ryfg",
        'y' to "tugh", 'u' to "yihj", 'i' to "uojk", 'o' to "ipkl", 'p' to "ol",
        'a' to "qwsz", 's' to "weadzx", 'd' to "ersfcx", 'f' to "rtdgvc", 'g' to "tyfhbv",
        'h' to "yugjnb", 'j' to "uihknm", 'k' to "iojlm", 'l' to "opk",
        'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb", 'b' to "vghn",
        'n' to "bhjm", 'm' to "njk",
    )

    // Confidence multipliers per edit kind: closer/likelier typos score higher.
    private const val TRANSPOSE = 0.95
    private const val SUBSTITUTE = 0.90 // neighbour-key substitution
    private const val DELETE = 0.85     // an extra letter was typed
    private const val INSERT = 0.80     // a letter was missing

    /**
     * Returns up to [limit] correction candidates for [word] (already normalized lowercase),
     * best edit-score first. Excludes [word] itself. Empty when nothing close is a real word.
     */
    fun corrections(
        word: String,
        dict: FrequencyDictionary,
        limit: Int,
    ): List<Candidate> {
        if (word.length < 2) return emptyList()
        // best edit score seen per corrected word
        val best = HashMap<String, Double>(64)

        fun consider(candidate: String, editScore: Double) {
            if (candidate == word || candidate.length < 2) return
            if (dict.frequencyOf(candidate) <= 0) return
            val prev = best[candidate]
            if (prev == null || editScore > prev) best[candidate] = editScore
        }

        val n = word.length
        val sb = StringBuilder(n + 1)

        // Deletions: drop each character.
        for (i in 0 until n) {
            sb.setLength(0)
            sb.append(word, 0, i).append(word, i + 1, n)
            consider(sb.toString(), DELETE)
        }
        // Transpositions: swap each adjacent pair.
        for (i in 0 until n - 1) {
            sb.setLength(0)
            sb.append(word, 0, i).append(word[i + 1]).append(word[i]).append(word, i + 2, n)
            consider(sb.toString(), TRANSPOSE)
        }
        // Substitutions: replace each char with a neighbour key.
        for (i in 0 until n) {
            val nbrs = NEIGHBORS[word[i]] ?: continue
            for (c in nbrs) {
                sb.setLength(0)
                sb.append(word, 0, i).append(c).append(word, i + 1, n)
                consider(sb.toString(), SUBSTITUTE)
            }
        }
        // Insertions: insert a neighbour key (or a duplicate of the adjacent char) at each gap.
        for (i in 0..n) {
            val anchor = if (i < n) word[i] else word[n - 1]
            val inserts = StringBuilder((NEIGHBORS[anchor] ?: "")).append(anchor)
            for (c in inserts) {
                sb.setLength(0)
                sb.append(word, 0, i).append(c).append(word, i, n)
                consider(sb.toString(), INSERT)
            }
        }

        if (best.isEmpty()) return emptyList()
        return best.entries
            .map { Candidate(it.key, dict.frequencyOf(it.key), it.value) }
            // Rank by edit confidence first, then word frequency.
            .sortedWith(compareByDescending<Candidate> { it.editScore }.thenByDescending { it.frequency })
            .take(limit)
    }
}
