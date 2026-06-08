package com.bornomala.keyboard.settings.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Centralized DataStore Preferences keys for keyboard settings. Kept in one place so the
 * key names are stable across releases (renaming a key silently drops the stored value).
 */
internal object SettingsPreferenceKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val KEYBOARD_THEME = stringPreferencesKey("keyboard_theme")
    val KEYBOARD_FONT = stringPreferencesKey("keyboard_font")
    val KEY_BORDER = booleanPreferencesKey("key_border")
    val HORIZONTAL_GAP_SCALE = floatPreferencesKey("horizontal_gap_scale")
    val VERTICAL_GAP_SCALE = floatPreferencesKey("vertical_gap_scale")
    val KEY_LABEL_SCALE = floatPreferencesKey("key_label_scale")
    val SUGGESTION_BAR_SCALE = floatPreferencesKey("suggestion_bar_scale")
    val BOTTOM_GAP_SCALE = floatPreferencesKey("bottom_gap_scale")
    val KEYBOARD_HEIGHT_SCALE = floatPreferencesKey("keyboard_height_scale")
    val KEY_PRESS_VIBRATION = booleanPreferencesKey("key_press_vibration")
    val KEY_PRESS_SOUND = booleanPreferencesKey("key_press_sound")
    val NUMBER_ROW_ENABLED = booleanPreferencesKey("number_row_enabled")
    val SUGGESTIONS_ENABLED = booleanPreferencesKey("suggestions_enabled")
    val CLIPBOARD_ENABLED = booleanPreferencesKey("clipboard_enabled")
    val AUTO_CAPITALIZATION = booleanPreferencesKey("auto_capitalization")
    val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
    val BANGLA_AUTO_COMMIT = booleanPreferencesKey("bangla_auto_commit")
    val BANGLA_PHONETIC_SUGGESTIONS = booleanPreferencesKey("bangla_phonetic_suggestions")
    val LEARN_FROM_TYPING = booleanPreferencesKey("learn_from_typing")
    val VOLUME_KEY_CURSOR_CONTROL = booleanPreferencesKey("volume_key_cursor_control")
}
