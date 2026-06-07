package com.bornomala.keyboard.ime.domain.port

import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Inbound port exposing the user preferences the keyboard renderer and input logic need.
 * Bound by the app to an adapter over the :settings module's DataStore repository. The
 * keyboard observes [settings] as a cold [Flow] (collected on its service scope) and also
 * persists the last-used language back so it can be restored next time.
 */
interface KeyboardSettingsPort {

    /** Reactive stream of the keyboard-relevant settings. */
    val settings: Flow<KeyboardSettings>

    /** Persists the last active language so it is restored on the next IME start. */
    suspend fun setLastLanguage(language: KeyboardLanguage)
}

/**
 * Immutable snapshot of the user preferences that affect keyboard behaviour and look.
 * Defaults are safe values used until DataStore emits (and by the in-module fallback).
 *
 * @param themeMode light/dark/system, fed to [com.bornomala.keyboard.theme.BornomalaTheme].
 * @param highContrast accessibility high-contrast mode.
 * @param keyHeightFraction multiplier (0..1 of the min..max range) for the key row height.
 * @param hapticsEnabled key-press vibration.
 * @param soundEnabled key-press click sound.
 * @param showNumberRow dedicated number row above the letters.
 * @param suggestionsEnabled suggestion bar on/off.
 * @param autoCapitalization auto-capitalize sentence starts in English.
 * @param doubleSpacePeriod insert ". " on double space.
 * @param banglaTransliterationEnabled route Bangla input through the engine (vs. fixed map).
 * @param lastLanguage language to restore on start.
 */
data class KeyboardSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keyboardTheme: KeyboardTheme = KeyboardTheme.SYSTEM,
    val keyboardFont: KeyboardFont = KeyboardFont.SYSTEM,
    val highContrast: Boolean = false,
    val keyHeightFraction: Float = 0.5f,
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val showNumberRow: Boolean = false,
    val suggestionsEnabled: Boolean = true,
    val autoCapitalization: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val banglaTransliterationEnabled: Boolean = true,
    val learnFromTyping: Boolean = true,
    val lastLanguage: KeyboardLanguage = KeyboardLanguage.ENGLISH,
)
