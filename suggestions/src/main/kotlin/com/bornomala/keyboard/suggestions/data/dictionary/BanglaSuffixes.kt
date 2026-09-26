package com.bornomala.keyboard.suggestions.data.dictionary

/**
 * Bangla suffix joining, ported from OpenBangla riti (`phonetic/suggestion.rs`, MPL-2.0).
 *
 * Inflected words (`boigulo` -> বইগুলো, `barite` -> বাড়িতে) are rarely in a dictionary as
 * such, so a roman input is also tried as *base + suffix*: the tail must be a known roman suffix
 * (`gulo`, `te`, `er`, … from riti's `suffix.json`), the base is resolved on its own, and the
 * two are joined with riti's orthographic rules ([join]).
 */
class BanglaSuffixes private constructor(private val suffixes: Map<String, String>) {

    val size: Int get() = suffixes.size

    /** The Bangla form of the roman suffix [roman], or null when it is not a suffix. */
    fun find(roman: String): String? = suffixes[roman]

    /**
     * Every split of [roman] into a base of at least [minBase] characters and a known suffix,
     * longest base first (the most specific reading wins ties later in ranking).
     */
    fun splits(roman: String, minBase: Int = MIN_BASE): List<Split> {
        if (roman.length <= minBase) return emptyList()
        var out: ArrayList<Split>? = null
        for (cut in roman.length - 1 downTo minBase) {
            val bangla = suffixes[roman.substring(cut)] ?: continue
            if (out == null) out = ArrayList(4)
            out.add(Split(roman.substring(0, cut), bangla))
        }
        return out ?: emptyList()
    }

    /** A base/suffix reading of a roman input: the roman base and the Bangla suffix. */
    data class Split(val base: String, val suffix: String)

    companion object {
        val EMPTY = BanglaSuffixes(emptyMap())

        /** Shortest base worth resolving on its own; one letter matches far too much. */
        const val MIN_BASE = 2

        /** Builds from the lines of riti's flat `{"roman": "বাংলা"}` JSON (one entry per line). */
        fun build(jsonLines: Sequence<String>): BanglaSuffixes {
            val map = HashMap<String, String>(1024)
            for (line in jsonLines) {
                val entry = BanglaAutocorrectTable.parseJsonEntry(line) ?: continue
                map[entry.first] = entry.second
            }
            return BanglaSuffixes(map)
        }

        /**
         * Joins a Bangla [base] word and a Bangla [suffix] the way riti does:
         *  - a base ending in a vowel (letter or sign) before a suffix starting with a vowel sign
         *    takes a য় in between (বই + ে -> বইয়ে);
         *  - a final ৎ becomes ত (জগৎ + ের -> জগতের);
         *  - a final ং becomes ঙ (রং + ের -> রঙের).
         */
        fun join(base: String, suffix: String): String {
            if (base.isEmpty()) return suffix
            if (suffix.isEmpty()) return base
            val last = base[base.length - 1]
            val first = suffix[0]
            val sb = StringBuilder(base.length + suffix.length + 1)
            when {
                isVowel(last) && isKar(first) -> sb.append(base).append(YA)
                last == KHANDA_TA -> sb.append(base, 0, base.length - 1).append(TA)
                last == ANUSVARA -> sb.append(base, 0, base.length - 1).append(NGA)
                else -> sb.append(base)
            }
            return sb.append(suffix).toString()
        }

        private fun isVowel(c: Char): Boolean = VOWELS.indexOf(c) >= 0

        private fun isKar(c: Char): Boolean = KARS.indexOf(c) >= 0

        /** Independent vowels and vowel signs, as riti's `is_vowel`. */
        private const val VOWELS =
            "অআইঈউঊঋএঐওঔঌৡ" +
                "ািীুূৃেৈোৌ"

        /** Vowel signs, as riti's `is_kar`. */
        private const val KARS = "ািীুূৃেৈোৌৄ"

        private const val YA = 'য়'
        private const val KHANDA_TA = 'ৎ'
        private const val TA = 'ত'
        private const val ANUSVARA = 'ং'
        private const val NGA = 'ঙ'
    }
}
