package com.bornomala.keyboard.suggestions.data.dictionary

/**
 * An immutable, in-memory bigram table: for a previous word, the ordered list of likely
 * next words. Built once from the bundled `prev<TAB>next1 next2 …` seed and reused for the
 * process lifetime, so lookups are a single [HashMap] hit with no per-keystroke allocation
 * beyond the returned view.
 *
 * The seed is intentionally small (common starts); on-device learning extends predictions
 * over time. Lookups are case-normalised by the caller (English lower-cased upstream).
 */
class BigramDictionary private constructor(
    private val table: Map<String, List<String>>,
) {

    /** Up to [limit] likely next words after [previousWord], best-first; empty if unknown. */
    fun nextWords(previousWord: String, limit: Int): List<String> {
        if (previousWord.isEmpty()) return emptyList()
        val all = table[previousWord] ?: return emptyList()
        return if (all.size <= limit) all else all.subList(0, limit)
    }

    companion object {
        val EMPTY = BigramDictionary(emptyMap())

        /**
         * Builds the table from raw `prev<TAB>next1 next2 …` lines. Blank lines and lines
         * starting with `#` are ignored. [lowercase] lower-cases keys and values (English).
         */
        fun build(lines: Sequence<String>, lowercase: Boolean): BigramDictionary {
            val table = HashMap<String, List<String>>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val prev = line.substring(0, tab).let { if (lowercase) it.lowercase() else it }
                val rest = line.substring(tab + 1).trim()
                if (prev.isEmpty() || rest.isEmpty()) continue
                val nexts = rest.split(' ')
                    .asSequence()
                    .map { if (lowercase) it.lowercase() else it }
                    .filter { it.isNotEmpty() }
                    .toList()
                if (nexts.isNotEmpty()) table[prev] = nexts
            }
            return BigramDictionary(table)
        }
    }
}
