package com.bornomala.keyboard.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.core.result.AppError
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.settings.domain.SettingsRepository
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.ThemeMode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * DataStore Preferences backed implementation of [SettingsRepository].
 *
 * The [DataStore] instance is provided by Hilt ([com.bornomala.keyboard.settings.di.SettingsModule])
 * so production and tests can supply different stores. All reads and writes run on the
 * injected IO dispatcher, never the main thread, satisfying the no-main-blocking rule.
 *
 * Read failures (e.g. [IOException] from a corrupt file) are recovered to empty
 * preferences which map to [Settings.DEFAULTS], keeping the keyboard usable instead of
 * crashing the input path.
 */
internal class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : SettingsRepository {

    override val settings: Flow<Settings> =
        dataStore.data
            .catch { throwable ->
                // DataStore only surfaces IOException for read failures; rethrow anything
                // else so genuine programming errors are not silently hidden.
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map(SettingsMapper::fromPreferences)
            .flowOn(dispatchers.io)

    override suspend fun setThemeMode(themeMode: ThemeMode): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.THEME_MODE] = themeMode.name }

    override suspend fun setKeyboardTheme(theme: KeyboardTheme): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.KEYBOARD_THEME] = theme.name }

    override suspend fun setKeyboardFont(font: KeyboardFont): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.KEYBOARD_FONT] = font.name }

    override suspend fun setKeyBorder(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.KEY_BORDER] = enabled }

    override suspend fun setHorizontalGapScale(scale: Float): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.HORIZONTAL_GAP_SCALE] = SettingsMapper.clampScale(scale) }

    override suspend fun setVerticalGapScale(scale: Float): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.VERTICAL_GAP_SCALE] = SettingsMapper.clampScale(scale) }

    override suspend fun setKeyLabelScale(scale: Float): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.KEY_LABEL_SCALE] = SettingsMapper.clampScale(scale) }

    override suspend fun setSuggestionBarScale(scale: Float): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.SUGGESTION_BAR_SCALE] = SettingsMapper.clampScale(scale) }

    override suspend fun setKeyboardHeightScale(scale: Float): AppResult<Unit> =
        edit { prefs ->
            prefs[SettingsPreferenceKeys.KEYBOARD_HEIGHT_SCALE] =
                SettingsMapper.clampHeightScale(scale)
        }

    override suspend fun setKeyPressVibration(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.KEY_PRESS_VIBRATION] = enabled }

    override suspend fun setKeyPressSound(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.KEY_PRESS_SOUND] = enabled }

    override suspend fun setNumberRowEnabled(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.NUMBER_ROW_ENABLED] = enabled }

    override suspend fun setSuggestionsEnabled(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.SUGGESTIONS_ENABLED] = enabled }

    override suspend fun setClipboardEnabled(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.CLIPBOARD_ENABLED] = enabled }

    override suspend fun setAutoCapitalization(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.AUTO_CAPITALIZATION] = enabled }

    override suspend fun setDoubleSpacePeriod(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.DOUBLE_SPACE_PERIOD] = enabled }

    override suspend fun setBanglaAutoCommit(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.BANGLA_AUTO_COMMIT] = enabled }

    override suspend fun setBanglaPhoneticSuggestions(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.BANGLA_PHONETIC_SUGGESTIONS] = enabled }

    override suspend fun setLearnFromTyping(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.LEARN_FROM_TYPING] = enabled }

    override suspend fun setVolumeKeyCursorControl(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.VOLUME_KEY_CURSOR_CONTROL] = enabled }

    override suspend fun resetToDefaults(): AppResult<Unit> =
        edit { prefs -> prefs.clear() }

    /**
     * Runs a single DataStore edit on the IO dispatcher and converts the outcome to an
     * [AppResult]. [CancellationException] is rethrown so structured concurrency
     * cancellation keeps propagating; everything else becomes [AppError.Storage].
     */
    private suspend inline fun edit(
        crossinline transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                dataStore.edit { prefs -> transform(prefs) }
                AppResult.Success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                AppResult.Failure(
                    AppError.Storage(
                        message = "Failed to persist setting",
                        cause = throwable,
                    ),
                )
            }
        }
}
