package com.bornomala.keyboard.ime.domain.model

/**
 * What the focused text field asks of the keyboard, resolved once per field from its
 * `EditorInfo`. It can only *restrict* the user's settings, never enable something they turned
 * off: e.g. suggestions show only when the setting is on **and** [allowSuggestions] is true.
 *
 * @param kind which bottom-row keys suit the field (`@` for email, `/` and `.com` for URLs).
 * @param isPassword a password or PIN field.
 * @param allowLearning false for passwords and fields that request no personalized learning
 *   (e.g. an incognito browser tab): nothing typed there is remembered.
 * @param allowSuggestions false for passwords and non-text fields (numbers, phones, dates).
 * @param allowAutoCorrect false where rewriting what was typed is never wanted: passwords,
 *   URLs and email addresses.
 * @param capsMode the auto-capitalization the field requests.
 * @param languageOverride a language to switch to for this field only (English for passwords,
 *   URLs, email and ASCII-only fields; otherwise the app's language hint), or null.
 * @param enterLabel the app's custom label for the Enter key (e.g. "Post"), or null.
 */
data class FieldProfile(
    val kind: FieldKind = FieldKind.TEXT,
    val isPassword: Boolean = false,
    val allowLearning: Boolean = true,
    val allowSuggestions: Boolean = true,
    val allowAutoCorrect: Boolean = true,
    val capsMode: CapsMode = CapsMode.SENTENCES,
    val languageOverride: KeyboardLanguage? = null,
    val enterLabel: String? = null,
) {
    companion object {
        /** No field bound (or no information): everything the user enabled applies. */
        val DEFAULT = FieldProfile()
    }
}

/** Field kinds that change the alpha layout's bottom row. */
enum class FieldKind { TEXT, EMAIL, URL }

/** Auto-capitalization requested by a field. */
enum class CapsMode {
    /** Never capitalize automatically. */
    NONE,

    /** Capitalize the first letter of each sentence. */
    SENTENCES,

    /** Capitalize the first letter of every word (names, titles). */
    WORDS,

    /** Capitalize every letter (codes, licence plates). */
    CHARACTERS,
}
