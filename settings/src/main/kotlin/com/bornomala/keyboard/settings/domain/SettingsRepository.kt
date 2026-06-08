package com.bornomala.keyboard.settings.domain

import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Stable contract for reading and mutating keyboard preferences. This is the seam every
 * other module depends on; the concrete DataStore implementation lives in the data layer
 * and is bound via Hilt ([com.bornomala.keyboard.settings.di.SettingsModule]).
 *
 * Reads are exposed as a cold [Flow] so consumers (the IME service, suggestion engine,
 * clipboard, theme) observe live updates. Writes are suspend functions returning
 * [AppResult] so callers can surface storage failures without crashing the input path.
 *
 * Implementations must never block the calling thread; all persistence happens on the
 * injected IO dispatcher.
 */
interface SettingsRepository {

    /**
     * Live stream of the full settings snapshot. Emits the current value immediately on
     * collection and again on every change. On a read error it emits [Settings.DEFAULTS]
     * rather than failing the stream, keeping the keyboard usable.
     */
    val settings: Flow<Settings>

    suspend fun setThemeMode(themeMode: ThemeMode): AppResult<Unit>

    suspend fun setKeyboardTheme(theme: KeyboardTheme): AppResult<Unit>

    suspend fun setKeyboardFont(font: KeyboardFont): AppResult<Unit>

    suspend fun setKeyBorder(enabled: Boolean): AppResult<Unit>

    suspend fun setHorizontalGapScale(scale: Float): AppResult<Unit>

    suspend fun setVerticalGapScale(scale: Float): AppResult<Unit>

    suspend fun setKeyLabelScale(scale: Float): AppResult<Unit>

    suspend fun setSuggestionBarScale(scale: Float): AppResult<Unit>

    suspend fun setBottomGapScale(scale: Float): AppResult<Unit>

    /**
     * Sets the keyboard height multiplier. The value is clamped to
     * [Settings.MIN_KEYBOARD_HEIGHT_SCALE]..[Settings.MAX_KEYBOARD_HEIGHT_SCALE] before
     * persisting.
     */
    suspend fun setKeyboardHeightScale(scale: Float): AppResult<Unit>

    suspend fun setKeyPressVibration(enabled: Boolean): AppResult<Unit>

    suspend fun setKeyPressSound(enabled: Boolean): AppResult<Unit>

    suspend fun setNumberRowEnabled(enabled: Boolean): AppResult<Unit>

    suspend fun setSuggestionsEnabled(enabled: Boolean): AppResult<Unit>

    suspend fun setClipboardEnabled(enabled: Boolean): AppResult<Unit>

    suspend fun setAutoCapitalization(enabled: Boolean): AppResult<Unit>

    suspend fun setDoubleSpacePeriod(enabled: Boolean): AppResult<Unit>

    suspend fun setBanglaAutoCommit(enabled: Boolean): AppResult<Unit>

    suspend fun setBanglaPhoneticSuggestions(enabled: Boolean): AppResult<Unit>

    suspend fun setLearnFromTyping(enabled: Boolean): AppResult<Unit>

    suspend fun setVolumeKeyCursorControl(enabled: Boolean): AppResult<Unit>

    /** Restores every preference to its default value in a single atomic edit. */
    suspend fun resetToDefaults(): AppResult<Unit>
}
