package com.bornomala.keyboard.glue

import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.port.KeyboardSettings
import com.bornomala.keyboard.ime.domain.port.KeyboardSettingsPort
import com.bornomala.keyboard.settings.domain.SettingsRepository
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.theme.KeyboardDimens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts the :settings module's [SettingsRepository] onto the keyboard's [KeyboardSettingsPort].
 *
 * It projects the full [Settings] snapshot down to the keyboard-relevant [KeyboardSettings]
 * and converts the user's height *scale* (a multiplier of the base row height) into the
 * 0..1 *fraction* of the renderer's min..max range that the keyboard consumes.
 *
 * The last-used language is held in process memory (the settings store has no such field in
 * V1) and combined into the emitted snapshot so the IME can restore it within a session.
 */
@Singleton
class KeyboardSettingsPortAdapter @Inject constructor(
    repository: SettingsRepository,
) : KeyboardSettingsPort {

    private val lastLanguage = MutableStateFlow(KeyboardLanguage.ENGLISH)

    override val settings: Flow<KeyboardSettings> =
        combine(repository.settings, lastLanguage) { s, language ->
            KeyboardSettings(
                themeMode = s.themeMode,
                keyboardTheme = s.keyboardTheme,
                highContrast = s.highContrast,
                keyHeightFraction = heightScaleToFraction(s.keyboardHeightScale),
                hapticsEnabled = s.keyPressVibration,
                soundEnabled = s.keyPressSound,
                showNumberRow = s.numberRowEnabled,
                suggestionsEnabled = s.suggestionsEnabled,
                autoCapitalization = s.autoCapitalization,
                doubleSpacePeriod = s.doubleSpacePeriod,
                // The Bangla layout is always phonetic; the per-feature toggles below govern
                // auto-commit / suggestions, not whether to route through the engine.
                banglaTransliterationEnabled = true,
                learnFromTyping = s.learnFromTyping,
                lastLanguage = language,
            )
        }

    override suspend fun setLastLanguage(language: KeyboardLanguage) {
        lastLanguage.value = language
    }

    private fun heightScaleToFraction(scale: Float): Float {
        val base = KeyboardDimens.keyRowHeight.value
        val min = KeyboardDimens.minKeyRowHeight.value
        val max = KeyboardDimens.maxKeyRowHeight.value
        val target = base * scale
        return ((target - min) / (max - min)).coerceIn(0f, 1f)
    }
}
