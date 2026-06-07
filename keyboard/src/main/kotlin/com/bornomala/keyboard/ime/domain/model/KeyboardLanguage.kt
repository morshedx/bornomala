package com.bornomala.keyboard.ime.domain.model

/**
 * The set of input languages the keyboard can switch between. The order also defines the
 * cycle order of the dedicated language-switch key (English -> Bangla -> English ...).
 *
 * Each value carries the IME subtype locale used in `@xml/method` so the service can map
 * a system-selected subtype back onto a [KeyboardLanguage] and vice versa.
 */
enum class KeyboardLanguage(
    val subtypeLocale: String,
    /** Human label shown on the spacebar / language key. */
    val displayName: String,
) {
    ENGLISH(subtypeLocale = "en_US", displayName = "English"),
    BANGLA(subtypeLocale = "bn_BD", displayName = "বাংলা"),
    ;

    /** Whether this language routes keystrokes through the transliteration engine. */
    val usesTransliteration: Boolean
        get() = this == BANGLA

    /** The next language in the round-robin cycle for the language-switch key. */
    fun next(): KeyboardLanguage {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        /** Resolves a subtype locale string (e.g. "bn_BD") to a language, defaulting to English. */
        fun fromSubtypeLocale(locale: String?): KeyboardLanguage =
            entries.firstOrNull { it.subtypeLocale.equals(locale, ignoreCase = true) } ?: ENGLISH
    }
}
