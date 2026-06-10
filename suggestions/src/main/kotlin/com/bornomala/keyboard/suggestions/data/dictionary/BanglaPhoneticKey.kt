package com.bornomala.keyboard.suggestions.data.dictionary

/**
 * Computes the ambiguity-collapsed phonetic key for a roman (Avro-style) Bangla input, so a
 * typed word can be matched against the bundled phonetic index (`bn_phonetic.txt`).
 *
 * Avro phonetic spelling is ambiguous: `r` may be র/ড়/ঢ়, `c`/`ch` may be চ/ছ, aspiration is
 * inconsistent, and the inherent vowel is often dropped. The key collapses all of these into a
 * single canonical form, identical to the one computed (at build time) from each dictionary
 * word's Bangla spelling. So `chara`, `chhara`, `chaRa` all reduce to the same key `cara`, which
 * the index maps to the real words (ছাড়া, চারা, …) ranked by frequency.
 *
 * Pure and allocation-light (one StringBuilder); safe to call on the input thread per keystroke.
 */
object BanglaPhoneticKey {

    // Aspirated digraphs collapse to the base consonant class (ch->c, kh->k, …).
    private val ASPIRATE: Map<String, Char> = mapOf(
        "kh" to 'k', "gh" to 'g', "ch" to 'c', "jh" to 'j', "th" to 't',
        "dh" to 'd', "ph" to 'p', "bh" to 'b', "sh" to 's',
    )

    // Single roman letters to consonant classes (interchangeable sounds share a class).
    private fun consonantClass(c: Char): Char? = when (c) {
        'c' -> 'c'
        'k', 'q' -> 'k'
        'g' -> 'g'
        't' -> 't'
        'd' -> 'd'
        'p', 'f' -> 'p'
        'b', 'v', 'w' -> 'b'
        'j', 'z' -> 'j'
        's' -> 's'
        'h' -> 'h'
        'm' -> 'm'
        'n' -> 'n'
        'l' -> 'l'
        'r' -> 'r'
        'y' -> 'y'
        else -> null
    }

    private fun isVowel(c: Char): Boolean = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'

    /** The canonical phonetic key for [roman], or empty if it has no usable phonetic content. */
    fun romanKey(roman: String): String {
        if (roman.isEmpty()) return ""
        // Capital 'R' is Avro ড় — fold to 'r' before lower-casing so it joins the r-class.
        val s = roman.replace('R', 'r').lowercase()
        val sb = StringBuilder(s.length)
        var i = 0
        val n = s.length
        while (i < n) {
            if (i + 1 < n) {
                val two = s.substring(i, i + 2)
                val asp = ASPIRATE[two]
                if (asp != null) {
                    appendClass(sb, asp)
                    i += 2
                    continue
                }
            }
            val c = s[i]
            if (isVowel(c)) {
                // Collapse a run of the same vowel (aa->a) and drop the inherent vowel 'o'.
                var j = i
                while (j < n && s[j] == c) j++
                if (c != 'o') sb.append(c)
                i = j
                continue
            }
            val cls = consonantClass(c)
            if (cls != null) {
                var j = i
                while (j < n && s[j] == c) j++ // collapse doubled consonants (kk->k)
                appendClass(sb, cls)
                i = j
                continue
            }
            i++ // ignore anything else
        }
        return sb.toString()
    }

    /** Appends a consonant class, collapsing it if it repeats the previous class (n+n -> n). */
    private fun appendClass(sb: StringBuilder, cls: Char) {
        if (sb.isNotEmpty() && sb.last() == cls) return
        sb.append(cls)
    }
}
