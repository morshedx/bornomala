package com.bornomala.keyboard.settings.domain.model

import androidx.compose.runtime.Immutable
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.ThemeMode

/**
 * Immutable snapshot of all user-configurable keyboard preferences.
 *
 * This is the single domain model exposed by [com.bornomala.keyboard.settings.domain.SettingsRepository].
 * Other feature modules (`:keyboard`, `:suggestions`, `:clipboard`, `:transliteration`)
 * observe the repository's `Flow<Settings>` and react to changes without touching the
 * underlying DataStore. Keeping this `@Immutable` keeps Compose recomposition cheap when
 * settings are read on the keyboard hot path.
 *
 * @property themeMode light / dark / system theme selection, consumed by `BornomalaTheme`.
 * @property keyboardHeightScale multiplier applied to the base key-row height; clamped to
 *   [MIN_KEYBOARD_HEIGHT_SCALE]..[MAX_KEYBOARD_HEIGHT_SCALE].
 * @property keyPressVibration emit haptic feedback on key press (requires VIBRATE perm).
 * @property keyPressSound play the system keypress sound on key press.
 * @property numberRowEnabled show a dedicated number row above the letters.
 * @property suggestionsEnabled show the suggestion bar and generate candidates.
 * @property clipboardEnabled record clipboard history and show the clipboard panel.
 * @property autoCapitalization auto-capitalize sentence starts (English layout).
 * @property doubleSpacePeriod insert ". " when space is tapped twice quickly.
 * @property banglaAutoCommit commit the transliterated Bangla word automatically on space
 *   / punctuation rather than waiting for explicit selection.
 * @property banglaPhoneticSuggestions show transliteration candidate alternatives in the
 *   suggestion bar while typing Bangla.
 * @property learnFromTyping allow the user dictionary to learn frequently typed words.
 * @property volumeKeyCursorControl move the text cursor with the volume keys while typing.
 */
@Immutable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keyboardTheme: KeyboardTheme = KeyboardTheme.SYSTEM,
    val keyboardFont: KeyboardFont = KeyboardFont.SYSTEM,
    val keyBorder: Boolean = false,
    val horizontalGapScale: Float = 1f,
    val verticalGapScale: Float = 1f,
    val keyLabelScale: Float = 1f,
    val suggestionBarScale: Float = 1f,
    val keyboardHeightScale: Float = DEFAULT_KEYBOARD_HEIGHT_SCALE,
    val keyPressVibration: Boolean = false,
    val keyPressSound: Boolean = false,
    val numberRowEnabled: Boolean = false,
    val suggestionsEnabled: Boolean = true,
    val clipboardEnabled: Boolean = true,
    val autoCapitalization: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val banglaAutoCommit: Boolean = true,
    val banglaPhoneticSuggestions: Boolean = true,
    val learnFromTyping: Boolean = true,
    val volumeKeyCursorControl: Boolean = true,
) {
    companion object {
        const val MIN_KEYBOARD_HEIGHT_SCALE: Float = 0.75f
        const val MAX_KEYBOARD_HEIGHT_SCALE: Float = 1.4f
        const val DEFAULT_KEYBOARD_HEIGHT_SCALE: Float = 1.0f

        /** Defaults snapshot, used as the initial emission and on read errors. */
        val DEFAULTS: Settings = Settings()
    }
}
