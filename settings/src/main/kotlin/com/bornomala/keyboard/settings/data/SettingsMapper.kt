package com.bornomala.keyboard.settings.data

import androidx.datastore.preferences.core.Preferences
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.ThemeMode

/**
 * Pure mapping between persisted [Preferences] and the domain [Settings] model.
 *
 * Reading falls back to [Settings.DEFAULTS] for any missing or malformed value so a
 * partially-written or corrupted store still yields a usable, fully-populated snapshot.
 * Kept allocation-light and side-effect free for easy unit testing.
 */
internal object SettingsMapper {

    fun fromPreferences(prefs: Preferences): Settings {
        val defaults = Settings.DEFAULTS
        return Settings(
            themeMode = prefs[SettingsPreferenceKeys.THEME_MODE]
                ?.let(::parseThemeMode)
                ?: defaults.themeMode,
            keyboardTheme = prefs[SettingsPreferenceKeys.KEYBOARD_THEME]
                ?.let(KeyboardTheme::fromName)
                ?: defaults.keyboardTheme,
            keyboardFont = prefs[SettingsPreferenceKeys.KEYBOARD_FONT]
                ?.let(KeyboardFont::fromName)
                ?: defaults.keyboardFont,
            keyBorder = prefs[SettingsPreferenceKeys.KEY_BORDER]
                ?: defaults.keyBorder,
            horizontalGapScale = prefs[SettingsPreferenceKeys.HORIZONTAL_GAP_SCALE]
                ?.let(::clampScale) ?: defaults.horizontalGapScale,
            verticalGapScale = prefs[SettingsPreferenceKeys.VERTICAL_GAP_SCALE]
                ?.let(::clampScale) ?: defaults.verticalGapScale,
            keyLabelScale = prefs[SettingsPreferenceKeys.KEY_LABEL_SCALE]
                ?.let(::clampScale) ?: defaults.keyLabelScale,
            suggestionBarScale = prefs[SettingsPreferenceKeys.SUGGESTION_BAR_SCALE]
                ?.let(::clampScale) ?: defaults.suggestionBarScale,
            bottomGapScale = prefs[SettingsPreferenceKeys.BOTTOM_GAP_SCALE]
                ?: defaults.bottomGapScale,
            keyboardHeightScale = prefs[SettingsPreferenceKeys.KEYBOARD_HEIGHT_SCALE]
                ?.let(::clampHeightScale)
                ?: defaults.keyboardHeightScale,
            keyPressVibration = prefs[SettingsPreferenceKeys.KEY_PRESS_VIBRATION]
                ?: defaults.keyPressVibration,
            keyPressSound = prefs[SettingsPreferenceKeys.KEY_PRESS_SOUND]
                ?: defaults.keyPressSound,
            numberRowEnabled = prefs[SettingsPreferenceKeys.NUMBER_ROW_ENABLED]
                ?: defaults.numberRowEnabled,
            suggestionsEnabled = prefs[SettingsPreferenceKeys.SUGGESTIONS_ENABLED]
                ?: defaults.suggestionsEnabled,
            autoCorrectEnabled = prefs[SettingsPreferenceKeys.AUTO_CORRECT_ENABLED]
                ?: defaults.autoCorrectEnabled,
            blockOffensiveWords = prefs[SettingsPreferenceKeys.BLOCK_OFFENSIVE_WORDS]
                ?: defaults.blockOffensiveWords,
            clipboardEnabled = prefs[SettingsPreferenceKeys.CLIPBOARD_ENABLED]
                ?: defaults.clipboardEnabled,
            autoCapitalization = prefs[SettingsPreferenceKeys.AUTO_CAPITALIZATION]
                ?: defaults.autoCapitalization,
            doubleSpacePeriod = prefs[SettingsPreferenceKeys.DOUBLE_SPACE_PERIOD]
                ?: defaults.doubleSpacePeriod,
            banglaAutoCommit = prefs[SettingsPreferenceKeys.BANGLA_AUTO_COMMIT]
                ?: defaults.banglaAutoCommit,
            banglaPhoneticSuggestions = prefs[SettingsPreferenceKeys.BANGLA_PHONETIC_SUGGESTIONS]
                ?: defaults.banglaPhoneticSuggestions,
            learnFromTyping = prefs[SettingsPreferenceKeys.LEARN_FROM_TYPING]
                ?: defaults.learnFromTyping,
            volumeKeyCursorControl = prefs[SettingsPreferenceKeys.VOLUME_KEY_CURSOR_CONTROL]
                ?: defaults.volumeKeyCursorControl,
        )
    }

    /** Tolerant parse of the stored theme name; unknown values fall back to SYSTEM. */
    fun parseThemeMode(raw: String): ThemeMode =
        when (raw) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
            else -> ThemeMode.SYSTEM
        }

    /** Configurator scales are bounded to a sane range. */
    fun clampScale(value: Float): Float = value.coerceIn(0.5f, 1.5f)

    fun clampHeightScale(value: Float): Float =
        value.coerceIn(
            Settings.MIN_KEYBOARD_HEIGHT_SCALE,
            Settings.MAX_KEYBOARD_HEIGHT_SCALE,
        )
}
