package com.bornomala.keyboard.ime.domain.input

/**
 * Spells a short English abbreviation out in Bangla letter names, the way Bangla writes
 * acronyms: `ips` -> আইপিএস, `sms` -> এসএমএস, `kg` -> কেজি. Phonetic transliteration turns these
 * into non-words (ইপ্স), so the spelled-out form is offered as a candidate.
 */
object BanglaAcronym {

    private const val MIN_LEN = 2
    private const val MAX_LEN = 5

    /** Bangla names of the English letters a–z. */
    private val LETTER_NAMES = arrayOf(
        "এ", "বি", "সি", "ডি", "ই", "এফ", "জি", "এইচ", "আই", "জে", "কে", "এল", "এম",
        "এন", "ও", "পি", "কিউ", "আর", "এস", "টি", "ইউ", "ভি", "ডব্লিউ", "এক্স", "ওয়াই", "জেড",
    )

    /** The spelled-out form of [roman], or null unless it is 2–5 ASCII letters. */
    fun spell(roman: String): String? {
        if (roman.length !in MIN_LEN..MAX_LEN) return null
        val sb = StringBuilder(roman.length * 3)
        for (c in roman) {
            val lower = c.lowercaseChar()
            if (lower !in 'a'..'z') return null
            sb.append(LETTER_NAMES[lower - 'a'])
        }
        return sb.toString()
    }

    /**
     * True when [roman] has no vowel letter (`sms`, `pc`, `kg`). Avro spells every Bangla word
     * with its vowels, so such input is an abbreviation and its phonetic rendering never a word.
     */
    fun isVowelless(roman: String): Boolean = roman.none { it.lowercaseChar() in "aeiou" }
}
