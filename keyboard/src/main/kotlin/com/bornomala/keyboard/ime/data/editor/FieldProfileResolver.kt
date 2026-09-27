package com.bornomala.keyboard.ime.data.editor

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.bornomala.keyboard.ime.domain.model.CapsMode
import com.bornomala.keyboard.ime.domain.model.FieldKind
import com.bornomala.keyboard.ime.domain.model.FieldProfile
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage

/**
 * Maps a field's `EditorInfo` values to a [FieldProfile]. Pure integer/string logic (the
 * framework constants are inlined), so it is unit-testable on the JVM; the service extracts the
 * primitives from `EditorInfo`.
 */
object FieldProfileResolver {

    /**
     * @param inputType `EditorInfo.inputType`.
     * @param imeOptions `EditorInfo.imeOptions`.
     * @param hintLanguageTags BCP-47 tags of `EditorInfo.hintLocales`, most preferred first.
     * @param actionLabel `EditorInfo.actionLabel`.
     */
    fun resolve(
        inputType: Int,
        imeOptions: Int,
        hintLanguageTags: List<String> = emptyList(),
        actionLabel: CharSequence? = null,
    ): FieldProfile {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isText = cls == InputType.TYPE_CLASS_TEXT

        val isPassword = when (cls) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        val kind = when {
            !isText -> FieldKind.TEXT
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
            variation == InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
            else -> FieldKind.TEXT
        }
        val noLearningFlag = imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        val forceAscii = imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0

        // Only free text is worth suggesting for; numbers, phones and dates are not words.
        //
        // TYPE_TEXT_FLAG_NO_SUGGESTIONS is deliberately ignored, as Gboard does. Apps set it on
        // everyday fields — Chrome's address bar, Google Keep notes — and mean "don't rewrite my
        // input" (Chrome's UrlBar says so), not "hide the strip"; honouring it blanked the strip
        // in those apps. Fields that must not be rewritten say so more specifically: passwords,
        // URLs and email addresses, all handled below.
        val allowSuggestions = isText && !isPassword
        val allowAutoCorrect = allowSuggestions && kind == FieldKind.TEXT &&
            variation != InputType.TYPE_TEXT_VARIATION_FILTER

        val englishOnly = isPassword || kind != FieldKind.TEXT || forceAscii
        val languageOverride = if (englishOnly) KeyboardLanguage.ENGLISH else hintLanguage(hintLanguageTags)

        val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val label = actionLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() && !noEnterAction }

        return FieldProfile(
            kind = kind,
            isPassword = isPassword,
            allowLearning = !isPassword && !noLearningFlag,
            allowSuggestions = allowSuggestions,
            allowAutoCorrect = allowAutoCorrect,
            capsMode = if (isText && !isPassword) capsMode(inputType) else CapsMode.NONE,
            languageOverride = languageOverride,
            enterLabel = label,
        )
    }

    /** The field's requested capitalization, as `TextUtils.getCapsMode` reads the same flags. */
    private fun capsMode(inputType: Int): CapsMode = when {
        inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> CapsMode.CHARACTERS
        inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> CapsMode.WORDS
        inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> CapsMode.SENTENCES
        else -> CapsMode.NONE
    }

    /** The first hinted language the keyboard types, or null. */
    private fun hintLanguage(tags: List<String>): KeyboardLanguage? {
        for (tag in tags) {
            when (tag.substringBefore('-').substringBefore('_').lowercase()) {
                "bn" -> return KeyboardLanguage.BANGLA
                "en" -> return KeyboardLanguage.ENGLISH
            }
        }
        return null
    }
}
