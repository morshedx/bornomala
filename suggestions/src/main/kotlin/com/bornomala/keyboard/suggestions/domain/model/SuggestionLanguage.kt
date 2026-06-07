package com.bornomala.keyboard.suggestions.domain.model

/**
 * The languages the suggestion subsystem can produce candidates for.
 *
 * The string [code] is the stable persistence key used for the user dictionary
 * `lang` column and for selecting the bundled frequency dictionary asset. It must
 * not change once data has been written, or learned words would be orphaned.
 */
enum class SuggestionLanguage(val code: String) {
    ENGLISH("en"),
    BANGLA("bn"),
    ;

    companion object {
        /** Resolves a persisted [code] back to a [SuggestionLanguage], or `null` if unknown. */
        fun fromCode(code: String): SuggestionLanguage? =
            entries.firstOrNull { it.code == code }
    }
}
