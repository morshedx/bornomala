package com.bornomala.keyboard.suggestions.data.dictionary

/**
 * An in-memory, read-only frequency dictionary for one language.
 *
 * Built once (lazily, off the main thread) from a bundled asset and then queried per
 * keystroke. To keep the hot path allocation-light and fast:
 * - words are stored in a single parallel array pair (no per-entry object),
 * - prefix lookup binary-searches the sorted word array and scans the matching run,
 *   ranking by the precomputed frequency, and
 * - results reuse a small bounded buffer.
 *
 * Words are expected pre-normalized (lowercase for English) and sorted ascending by
 * the [build] factory, which is the only supported way to construct an instance.
 */
class FrequencyDictionary private constructor(
    private val words: Array<String>,
    private val frequencies: IntArray,
    private val maxFrequency: Int,
) {

    /** Number of entries; primarily for tests and diagnostics. */
    val size: Int get() = words.size

    /** Exact frequency for [word], or 0 if not present. */
    fun frequencyOf(word: String): Int {
        val idx = words.binarySearch(word)
        return if (idx >= 0) frequencies[idx] else 0
    }

    /** Highest frequency in the dictionary, used to normalize scores into [0,1]. */
    fun maxFrequency(): Int = maxFrequency

    /**
     * Appends up to [limit] entries whose word starts with [prefix] into [out],
     * ordered by descending frequency. Returns the number appended. Allocates no
     * intermediate collections; the caller owns [out] and any pooling of it.
     */
    fun collectByPrefix(prefix: String, limit: Int, out: MutableList<DictionaryHit>): Int {
        if (limit <= 0) return 0
        var start = lowerBound(prefix)
        if (start < 0 || start >= words.size) return 0

        // Gather the contiguous run of words sharing the prefix, then take the top-N
        // by frequency. The run is typically short, so a simple insertion into a
        // bounded list is cheaper than sorting the whole run.
        val before = out.size
        var i = start
        while (i < words.size && words[i].startsWith(prefix)) {
            insertTopN(out, before, limit, DictionaryHit(words[i], frequencies[i]))
            i++
        }
        return out.size - before
    }

    /**
     * Binary search for the first index whose word is >= [prefix]. Returns the
     * insertion point so callers can scan forward through the matching run.
     */
    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun insertTopN(out: MutableList<DictionaryHit>, regionStart: Int, limit: Int, hit: DictionaryHit) {
        val regionSize = out.size - regionStart
        if (regionSize < limit) {
            // Insert keeping the region sorted by descending frequency.
            var j = out.size
            out.add(hit)
            while (j > regionStart && out[j - 1].frequency < hit.frequency) {
                out[j] = out[j - 1]
                j--
            }
            out[j] = hit
            return
        }
        // Region full: replace the smallest (last) if this is bigger.
        val lastIdx = out.size - 1
        if (out[lastIdx].frequency >= hit.frequency) return
        var j = lastIdx
        out[j] = hit
        while (j > regionStart && out[j - 1].frequency < out[j].frequency) {
            val tmp = out[j - 1]
            out[j - 1] = out[j]
            out[j] = tmp
            j--
        }
    }

    companion object {
        /**
         * Builds a dictionary from raw `word<TAB>frequency` lines (blank lines and
         * lines starting with `#` ignored). Words are lowercased only when
         * [lowercase] is true (English). The result is sorted ascending by word so
         * prefix queries can binary-search.
         */
        fun build(lines: Sequence<String>, lowercase: Boolean): FrequencyDictionary {
            val map = HashMap<String, Int>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                val word: String
                val freq: Int
                if (tab >= 0) {
                    word = line.substring(0, tab).let { if (lowercase) it.lowercase() else it }
                    freq = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                } else {
                    word = if (lowercase) line.lowercase() else line
                    freq = 1
                }
                if (word.isEmpty()) continue
                // Keep the highest frequency if a word appears twice.
                val existing = map[word]
                if (existing == null || freq > existing) map[word] = freq
            }
            val sortedWords = map.keys.toTypedArray()
            sortedWords.sort()
            val freqs = IntArray(sortedWords.size)
            var max = 0
            for (i in sortedWords.indices) {
                val f = map[sortedWords[i]] ?: 0
                freqs[i] = f
                if (f > max) max = f
            }
            return FrequencyDictionary(sortedWords, freqs, max)
        }
    }
}

/**
 * A lightweight prefix-match result: the dictionary [word] and its raw [frequency].
 * Kept as a small data class (not boxed pair) so collection into pooled lists is cheap.
 */
data class DictionaryHit(val word: String, val frequency: Int)
