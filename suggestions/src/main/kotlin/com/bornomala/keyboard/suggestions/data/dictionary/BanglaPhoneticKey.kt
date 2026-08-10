package com.bornomala.keyboard.suggestions.data.dictionary

/**
 * Computes the ambiguity-collapsed phonetic key shared by roman (Avro-style) input and real
 * Bangla words, so a typed word can be matched against the bundled phonetic index
 * (`bn_phonetic.txt`) and against words the user has taught the keyboard.
 *
 * Avro phonetic spelling is ambiguous: `r` may be র/ড়/ঢ়, `c`/`ch` may be চ/ছ, aspiration is
 * inconsistent, and the inherent vowel is often dropped. The key collapses all of these into a
 * single canonical form. [romanKey] derives it from what the user typed and [banglaKey] derives
 * the identical form from a word's Bangla spelling — the index is generated from [banglaKey], so
 * `chara`, `chhara`, `chaRa` all reduce to `cara`, which maps to ছাড়া, চারা, …
 *
 * The inherent vowel (`o`) is dropped from the key but still acts as a *barrier*: two same-class
 * consonants collapse into one only when they are genuinely adjacent (a conjunct, or a doubled
 * roman letter). Without that barrier `shosha` (শশা) would reduce to `sa` and resolve to সা — a
 * different word entirely.
 *
 * Both directions are pure and allocation-light (one StringBuilder, no substrings, no regex);
 * [romanKey] is safe to call on the input thread per keystroke.
 */
object BanglaPhoneticKey {

    /** The canonical phonetic key for roman input [roman], or empty if it has no phonetic content. */
    fun romanKey(roman: String): String {
        val n = roman.length
        if (n == 0) return ""
        val sb = StringBuilder(n)
        // True when a vowel (including the dropped inherent `o`) separates the next consonant from
        // the previous one, which blocks same-class collapsing. Word start counts as a barrier.
        var vowelSince = true
        var i = 0
        while (i < n) {
            val c = roman[i].lowercaseChar()
            if (isVowel(c)) {
                // Collapse a run of the same vowel (aa -> a); the inherent `o` barriers but emits nothing.
                var j = i + 1
                while (j < n && roman[j].lowercaseChar() == c) j++
                if (c != 'o') sb.append(c)
                vowelSince = true
                i = j
                continue
            }
            if (c == 'x') {
                // Avro `x` is ক্স: two classes, contiguous with each other.
                vowelSince = appendClass(sb, 'k', vowelSince)
                vowelSince = appendClass(sb, 's', vowelSince)
                i++
                continue
            }
            val cls = consonantClass(c)
            if (cls == null) {
                i++ // digits, punctuation, chandrabindu markers (`^`), … carry no key weight
                continue
            }
            // Absorb aspirating `h`s (kh, gh, ch, jh, th, dh, ph, bh, sh -> the base class).
            // Repeated so the doubled Avro form (`chh` for ছ, `shh` for ষ) collapses the same way.
            var j = i + 1
            while (j < n && roman[j].lowercaseChar() == 'h' && isAspirable(c)) j++
            vowelSince = appendClass(sb, cls, vowelSince)
            i = j
        }
        return sb.toString()
    }

    /**
     * The canonical phonetic key for a Bangla-script [word] — the same form [romanKey] produces
     * for that word's roman spelling. Empty when the word carries no Bangla phonetic content
     * (digits, punctuation, latin text).
     */
    fun banglaKey(word: String): String {
        val n = word.length
        if (n == 0) return ""
        val sb = StringBuilder(n)
        var vowelSince = true
        // Set by a preceding hasant: the next consonant is part of a conjunct (so it is contiguous
        // with the previous one) and `য` is the ja-phala, typed `y` rather than `j`.
        var afterHasant = false
        var i = 0
        while (i < n) {
            val ch = word[i]
            if (ch == HASANT) {
                afterHasant = true
                i++
                continue
            }
            val vowel = vowelSound(ch)
            if (vowel != NO_SOUND) {
                // Independent vowels and vowel signs alike barrier; `o` sounds emit nothing.
                when (vowel) {
                    RI_VOWEL -> {
                        vowelSince = appendClass(sb, 'r', vowelSince)
                        sb.append('i')
                    }
                    'o' -> Unit
                    else -> sb.append(vowel)
                }
                vowelSince = true
                afterHasant = false
                i++
                continue
            }
            val cls = consonantSound(ch, afterHasant)
            if (cls == NO_SOUND) {
                i++ // chandrabindu, visarga, ZWNJ/ZWJ, digits, punctuation
                continue
            }
            // ং/ঙ and খণ্ড-ত are pure codas: unlike ordinary consonants they carry no inherent vowel.
            val carriesInherentVowel = cls != ANUSVARA && ch != KHANDA_TA
            i++
            if (i < n && word[i] == NUKTA) {
                // ড়/ঢ় join the r-class and য় the y-class, whether precomposed or letter + nukta.
                i++
                vowelSince = appendClass(sb, nuktaClass(cls), vowelSince)
            } else if (cls == ANUSVARA) {
                vowelSince = appendClass(sb, 'n', vowelSince)
                vowelSince = appendClass(sb, 'g', vowelSince)
            } else {
                vowelSince = appendClass(sb, cls, vowelSince)
            }
            afterHasant = false
            // The inherent `o` is dropped from the key but still barriers the next consonant.
            if (carriesInherentVowel && !hasExplicitVowelNext(word, i)) vowelSince = true
        }
        return sb.toString()
    }

    /**
     * True when the next phonetically meaningful char at or after [from] is a vowel sign or a
     * hasant — i.e. the consonant before it does *not* sound its inherent vowel. Skips the marks
     * that leave the vowel untouched (chandrabindu, visarga, nukta, zero-width joiners).
     */
    private fun hasExplicitVowelNext(word: String, from: Int): Boolean {
        var i = from
        while (i < word.length) {
            val ch = word[i]
            if (ch == HASANT) return true
            if (vowelSound(ch) != NO_SOUND) return isVowelSign(ch)
            if (!isTransparent(ch)) return false
            i++
        }
        return false
    }

    /**
     * Appends a consonant class, collapsing it into the previous one only when the two are
     * genuinely adjacent (no vowel between). Returns the new `vowelSince` state — always false,
     * since a consonant ends the vowel run.
     */
    private fun appendClass(sb: StringBuilder, cls: Char, vowelSince: Boolean): Boolean {
        if (vowelSince || sb.isEmpty() || sb.last() != cls) sb.append(cls)
        return false
    }

    private fun isVowel(c: Char): Boolean = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'

    /** Consonants whose following `h` is an aspiration marker rather than a sound of its own. */
    private fun isAspirable(c: Char): Boolean =
        c == 'k' || c == 'g' || c == 'c' || c == 'j' || c == 't' ||
            c == 'd' || c == 'p' || c == 'b' || c == 's'

    /** Single roman letters to consonant classes (interchangeable sounds share a class). */
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

    /**
     * The consonant class of a Bangla letter, or [NO_SOUND] when it is not a consonant.
     * [afterHasant] distinguishes the ja-phala (`্য`, typed `y`) from a standalone য (typed `j`).
     */
    private fun consonantSound(ch: Char, afterHasant: Boolean): Char = when (ch) {
        'ক', 'খ' -> 'k'
        'গ', 'ঘ' -> 'g'
        'ঙ', 'ং' -> ANUSVARA
        'চ', 'ছ' -> 'c'
        'জ', 'ঝ' -> 'j'
        'ঞ', 'ণ', 'ন' -> 'n'
        'ট', 'ঠ', 'ত', 'থ', 'ৎ' -> 't'
        'ড', 'ঢ', 'দ', 'ধ' -> 'd'
        'প', 'ফ' -> 'p'
        'ব', 'ভ' -> 'b'
        'ম' -> 'm'
        'য' -> if (afterHasant) 'y' else 'j'
        'র' -> 'r'
        'ল' -> 'l'
        'শ', 'ষ', 'স' -> 's'
        'হ' -> 'h'
        '\u09DC', '\u09DD' -> 'r' // ড় ঢ় precomposed, as the transliteration engine emits them
        '\u09DF' -> 'y' // য় precomposed
        else -> NO_SOUND
    }

    /** The class a letter takes once a nukta is applied: ড/ঢ -> r, য -> y; others unchanged. */
    private fun nuktaClass(cls: Char): Char = when (cls) {
        'd' -> 'r'
        'j' -> 'y'
        else -> cls
    }

    /**
     * The roman vowel a Bangla vowel (independent letter or sign) reduces to, or [NO_SOUND].
     * `o` sounds contribute nothing to the key but still barrier; [RI_VOWEL] is ঋ/ৃ (`ri`).
     */
    private fun vowelSound(ch: Char): Char = when (ch) {
        'অ', 'ও', 'ো' -> 'o'
        'আ', 'া' -> 'a'
        'ই', 'ঈ', 'ি', 'ী' -> 'i'
        'উ', 'ঊ', 'ু', 'ূ' -> 'u'
        'ঋ', 'ৃ' -> RI_VOWEL
        'এ', 'ে' -> 'e'
        'ঐ', 'ৈ' -> 'i' // `oi`: the leading `o` drops, leaving `i`
        'ঔ', 'ৌ' -> 'u' // `ou`: likewise
        else -> NO_SOUND
    }

    private fun isVowelSign(ch: Char): Boolean =
        ch == 'া' || ch == 'ি' || ch == 'ী' || ch == 'ু' || ch == 'ূ' ||
            ch == 'ৃ' || ch == 'ে' || ch == 'ৈ' || ch == 'ো' || ch == 'ৌ'

    /** Marks that sit on a syllable without changing its vowel or its consonant class. */
    private fun isTransparent(ch: Char): Boolean =
        ch == CHANDRABINDU || ch == VISARGA || ch == NUKTA || ch == ZWNJ || ch == ZWJ

    private const val HASANT = '্'
    private const val NUKTA = '়'
    private const val CHANDRABINDU = 'ঁ'
    private const val VISARGA = 'ঃ'
    private const val KHANDA_TA = 'ৎ'
    private const val ZWNJ = '\u200C'
    private const val ZWJ = '\u200D'

    /** Sentinel for "this character has no phonetic weight"; never a real class or vowel. */
    private const val NO_SOUND = '\u0000'

    /** Internal markers expanded by [banglaKey]; never appear in a finished key. */
    private const val ANUSVARA = '\u0001'
    private const val RI_VOWEL = '\u0002'
}
