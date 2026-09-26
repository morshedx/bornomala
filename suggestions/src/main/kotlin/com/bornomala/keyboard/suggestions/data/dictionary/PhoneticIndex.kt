package com.bornomala.keyboard.suggestions.data.dictionary

/**
 * The bundled Bangla phonetic index (`bn_phonetic.txt`) in a compact, allocation-light form.
 *
 * With OpenBangla riti's dictionary merged in the index holds well over 100k keys, so a
 * `HashMap<String, List<String>>` would cost tens of megabytes of object overhead. Instead all
 * keys live in one sorted blob and all words in another, addressed by [IntArray] offsets; a
 * lookup is a binary search over the key blob that compares in place (no substring per probe) and
 * only allocates the handful of words it returns.
 *
 * Each key carries two word groups, both best-first:
 *  - **trusted** — corpus words with real frequencies; the keyboard may auto-pick these;
 *  - **extra** — suggest-only words from riti's dictionary: offered and treated as real words,
 *    but never silently swapped in for what the user typed.
 *
 * Line format: `key<TAB>trusted words…[<TAB>extra words…]`, words space-separated. `#` lines and
 * blank lines are ignored.
 */
class PhoneticIndex private constructor(
    private val keys: String,
    /** Start of key `i` in [keys]; `keyStarts[size]` is the end. */
    private val keyStarts: IntArray,
    private val words: String,
    /** Start of key `i`'s trusted words in [words]. */
    private val trustedStarts: IntArray,
    /** Start of key `i`'s extra words in [words]; `trustedStarts[i + 1]` ends them. */
    private val extraStarts: IntArray,
) {

    val size: Int get() = keyStarts.size - 1

    /** Trusted then extra words for [key]; empty groups when the key is absent. */
    fun lookup(key: String, limit: Int): PhoneticHits {
        val i = indexOf(key)
        if (i < 0 || limit <= 0) return PhoneticHits.EMPTY
        val trusted = split(trustedStarts[i], extraStarts[i], limit)
        val extra = split(extraStarts[i], trustedStarts[i + 1], limit)
        return PhoneticHits(trusted, extra)
    }

    /** True when [key] has any word, trusted or extra. */
    fun contains(key: String): Boolean = indexOf(key) >= 0

    private fun indexOf(key: String): Int {
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = compareKey(mid, key)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /** Lexicographic comparison of key [i] against [key], in place (UTF-16 code-unit order). */
    private fun compareKey(i: Int, key: String): Int {
        val start = keyStarts[i]
        val len = keyStarts[i + 1] - start
        val n = minOf(len, key.length)
        for (j in 0 until n) {
            val d = keys[start + j] - key[j]
            if (d != 0) return d
        }
        return len - key.length
    }

    private fun split(from: Int, to: Int, limit: Int): List<String> {
        if (from >= to) return emptyList()
        val out = ArrayList<String>(minOf(limit, 8))
        var start = from
        var i = from
        while (i <= to && out.size < limit) {
            if (i == to || words[i] == ' ') {
                if (i > start) out.add(words.substring(start, i))
                start = i + 1
            }
            i++
        }
        return out
    }

    companion object {
        val EMPTY: PhoneticIndex = PhoneticIndex("", intArrayOf(0), "", intArrayOf(0), intArrayOf(0))

        fun build(lines: Sequence<String>): PhoneticIndex {
            val entries = ArrayList<Entry>(INITIAL_CAPACITY)
            for (raw in lines) {
                if (raw.isBlank() || raw.startsWith('#')) continue
                val firstTab = raw.indexOf('\t')
                if (firstTab <= 0) continue
                val secondTab = raw.indexOf('\t', firstTab + 1)
                val key = raw.substring(0, firstTab).trim()
                val trusted = (if (secondTab < 0) raw.substring(firstTab + 1) else raw.substring(firstTab + 1, secondTab)).trim()
                val extra = if (secondTab < 0) "" else raw.substring(secondTab + 1).trim()
                if (key.isNotEmpty() && (trusted.isNotEmpty() || extra.isNotEmpty())) {
                    entries.add(Entry(key, trusted, extra))
                }
            }
            // The generator writes keys sorted; sort anyway so a hand-edited or test source can't
            // silently break the binary search. Duplicate keys keep the first occurrence.
            entries.sortWith { a, b -> a.key.compareTo(b.key) }

            val keys = StringBuilder()
            val words = StringBuilder()
            val keyStarts = IntArray(entries.size + 1)
            val trustedStarts = IntArray(entries.size + 1)
            val extraStarts = IntArray(entries.size + 1)
            var n = 0
            var previous: String? = null
            for (e in entries) {
                if (e.key == previous) continue
                previous = e.key
                keyStarts[n] = keys.length
                keys.append(e.key)
                trustedStarts[n] = words.length
                words.append(e.trusted)
                extraStarts[n] = words.length
                if (e.extra.isNotEmpty()) {
                    // A separator so the last trusted word and first extra word don't fuse.
                    if (e.trusted.isNotEmpty()) {
                        words.append(' ')
                        extraStarts[n] = words.length
                    }
                    words.append(e.extra)
                }
                words.append(' ')
                n++
            }
            keyStarts[n] = keys.length
            trustedStarts[n] = words.length
            return PhoneticIndex(
                keys = keys.toString(),
                keyStarts = keyStarts.copyOf(n + 1),
                words = words.toString(),
                trustedStarts = trustedStarts.copyOf(n + 1),
                extraStarts = extraStarts.copyOf(n + 1),
            )
        }

        private class Entry(val key: String, val trusted: String, val extra: String)

        private const val INITIAL_CAPACITY = 1 shl 17
    }
}

/** Words for one phonetic key: [trusted] may be auto-picked, [extra] are suggest-only. */
data class PhoneticHits(val trusted: List<String>, val extra: List<String>) {
    companion object {
        val EMPTY = PhoneticHits(emptyList(), emptyList())
    }
}
