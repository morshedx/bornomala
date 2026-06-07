package com.bornomala.keyboard.settings.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure unit tests for the preferences <-> domain mapping, covering defaults, round-trip,
 * tolerant theme parsing, and height-scale clamping.
 */
class SettingsMapperTest {

    @Test
    fun `empty preferences map to defaults`() {
        val result = SettingsMapper.fromPreferences(emptyPreferences())
        assertThat(result).isEqualTo(Settings.DEFAULTS)
    }

    @Test
    fun `stored values round-trip into the model`() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.THEME_MODE to ThemeMode.DARK.name,
            SettingsPreferenceKeys.KEYBOARD_HEIGHT_SCALE to 1.2f,
            SettingsPreferenceKeys.KEY_PRESS_VIBRATION to true,
            SettingsPreferenceKeys.NUMBER_ROW_ENABLED to true,
            SettingsPreferenceKeys.SUGGESTIONS_ENABLED to false,
            SettingsPreferenceKeys.BANGLA_AUTO_COMMIT to false,
        )

        val result = SettingsMapper.fromPreferences(prefs)

        assertThat(result.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(result.keyboardHeightScale).isEqualTo(1.2f)
        assertThat(result.keyPressVibration).isTrue()
        assertThat(result.numberRowEnabled).isTrue()
        assertThat(result.suggestionsEnabled).isFalse()
        assertThat(result.banglaAutoCommit).isFalse()
        // Unset keys keep their defaults.
        assertThat(result.clipboardEnabled).isEqualTo(Settings.DEFAULTS.clipboardEnabled)
    }

    @Test
    fun `unknown theme string falls back to system`() {
        assertThat(SettingsMapper.parseThemeMode("PURPLE")).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `height scale is clamped to bounds`() {
        assertThat(SettingsMapper.clampHeightScale(0.1f))
            .isEqualTo(Settings.MIN_KEYBOARD_HEIGHT_SCALE)
        assertThat(SettingsMapper.clampHeightScale(9f))
            .isEqualTo(Settings.MAX_KEYBOARD_HEIGHT_SCALE)
        assertThat(SettingsMapper.clampHeightScale(1.0f)).isEqualTo(1.0f)
    }

    @Test
    fun `out-of-range stored scale is clamped on read`() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.KEYBOARD_HEIGHT_SCALE to 5.0f,
        )
        val result = SettingsMapper.fromPreferences(prefs)
        assertThat(result.keyboardHeightScale)
            .isEqualTo(Settings.MAX_KEYBOARD_HEIGHT_SCALE)
    }
}
