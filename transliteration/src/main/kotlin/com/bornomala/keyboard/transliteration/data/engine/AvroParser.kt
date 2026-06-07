package com.bornomala.keyboard.transliteration.data.engine

/**
 * Faithful reimplementation of the OmicronLab Avro Phonetic parsing algorithm (as in
 * jsAvroPhonetic / pyAvroPhonetic), operating over the bundled [AvroDictionary].
 *
 * Algorithm summary (longest-match-first with contextual rules):
 *  1. Normalize the input case: every character is lowercased EXCEPT the case-sensitive
 *     letters (`o i u d g j n r s t y z`), whose case is meaningful in Avro.
 *  2. Scan left to right. At each position try the longest possible chunk (down to length 1)
 *     that exactly matches a pattern's `find`.
 *  3. If that pattern has contextual `rules`, evaluate them in order; the first rule whose
 *     `matches` all hold (prefix/suffix against vowel/consonant/punctuation/exact, with `!`
 *     negation) wins and its `replace` is emitted. Otherwise the pattern's default `replace`
 *     is used.
 *  4. Characters matching no pattern are emitted verbatim.
 *
 * Out-of-range neighbours (start/end of the word) count as punctuation, matching the
 * reference implementation, so word-boundary rules (independent vowels, etc.) fire correctly.
 *
 * Hot-path discipline: patterns are indexed in a hash map keyed by `find`; matching is a
 * bounded scan from [maxPatternLength] down to 1 with map lookups — no regex, no per-call
 * allocation beyond the output buffer. Stateless and thread-safe; a single instance is shared.
 */
class AvroParser private constructor(
    private val patterns: Map<String, AvroPattern>,
    private val maxPatternLength: Int,
    private val vowels: String,
    private val consonants: String,
    private val caseSensitive: String,
) {

    /** Transliterates a full Latin [input] word into Bangla. */
    fun parse(input: String): String {
        if (input.isEmpty()) return ""
        val fixed = normalizeCase(input)
        val n = fixed.length
        val out = StringBuilder(n * 2)
        var cur = 0
        while (cur < n) {
            val start = cur
            var matched = false
            var chunkLen = minOf(maxPatternLength, n - start)
            while (chunkLen > 0) {
                val end = start + chunkLen
                val pattern = patterns[fixed.substring(start, end)]
                if (pattern != null) {
                    out.append(resolve(pattern, fixed, start, end))
                    cur = end - 1
                    matched = true
                    break
                }
                chunkLen--
            }
            if (!matched) out.append(fixed[cur])
            cur++
        }
        return out.toString()
    }

    private fun resolve(pattern: AvroPattern, fixed: String, start: Int, end: Int): String {
        for (rule in pattern.rules) {
            if (rule.matches.all { matchHolds(it, fixed, start, end) }) {
                return rule.replace
            }
        }
        return pattern.replace
    }

    private fun matchHolds(match: AvroMatch, fixed: String, start: Int, end: Int): Boolean {
        val negative = match.scope.startsWith("!")
        val scope = if (negative) match.scope.substring(1) else match.scope
        val isPrefix = match.type == TYPE_PREFIX

        val holds = when (scope) {
            SCOPE_PUNCTUATION -> {
                val idx = if (isPrefix) start - 1 else end
                idx < 0 || idx >= fixed.length || isPunctuation(fixed[idx])
            }
            SCOPE_VOWEL -> {
                val idx = if (isPrefix) start - 1 else end
                idx in fixed.indices && isVowel(fixed[idx])
            }
            SCOPE_CONSONANT -> {
                val idx = if (isPrefix) start - 1 else end
                idx in fixed.indices && isConsonant(fixed[idx])
            }
            SCOPE_EXACT -> {
                val value = match.value ?: ""
                if (isPrefix) exactAt(fixed, start - value.length, start, value)
                else exactAt(fixed, end, end + value.length, value)
            }
            else -> false
        }
        return holds != negative
    }

    private fun exactAt(fixed: String, from: Int, to: Int, value: String): Boolean =
        from >= 0 && to <= fixed.length && fixed.substring(from, to) == value

    private fun isVowel(c: Char): Boolean = vowels.indexOf(c.lowercaseChar()) >= 0

    private fun isConsonant(c: Char): Boolean = consonants.indexOf(c.lowercaseChar()) >= 0

    private fun isPunctuation(c: Char): Boolean = !isVowel(c) && !isConsonant(c)

    private fun isCaseSensitive(c: Char): Boolean = caseSensitive.indexOf(c.lowercaseChar()) >= 0

    private fun normalizeCase(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) sb.append(if (isCaseSensitive(c)) c else c.lowercaseChar())
        return sb.toString()
    }

    companion object {
        private const val TYPE_PREFIX = "prefix"
        private const val SCOPE_PUNCTUATION = "punctuation"
        private const val SCOPE_VOWEL = "vowel"
        private const val SCOPE_CONSONANT = "consonant"
        private const val SCOPE_EXACT = "exact"

        /** Builds a parser from a parsed [AvroDictionary]. */
        fun from(dictionary: AvroDictionary): AvroParser {
            val data = dictionary.data
            val map = HashMap<String, AvroPattern>(data.patterns.size * 2)
            var maxLen = 1
            for (p in data.patterns) {
                map[p.find] = p
                if (p.find.length > maxLen) maxLen = p.find.length
            }
            return AvroParser(
                patterns = map,
                maxPatternLength = maxLen,
                vowels = data.vowel,
                consonants = data.consonant,
                caseSensitive = data.caseSensitive,
            )
        }

        /** Loads the bundled OmicronLab dictionary and builds a parser. */
        fun load(): AvroParser = from(AvroDictionaryLoader.load())
    }
}
