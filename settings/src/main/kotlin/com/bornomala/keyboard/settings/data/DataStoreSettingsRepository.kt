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

    override suspend fun setBottomGapScale(scale: Float): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.BOTTOM_GAP_SCALE] = scale }

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

    override suspend fun setAutoCorrectEnabled(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.AUTO_CORRECT_ENABLED] = enabled }

    override suspend fun setBlockOffensiveWords(enabled: Boolean): AppResult<Unit> =
        edit { prefs -> prefs[SettingsPreferenceKeys.BLOCK_OFFENSIVE_WORDS] = enabled }

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

    override suspend fun replaceAll(settings: Settings): AppResult<Unit> =
        edit { prefs ->
            prefs.clear()
            val keys = SettingsPreferenceKeys
            prefs[keys.THEME_MODE] = settings.themeMode.name
            prefs[keys.KEYBOARD_THEME] = settings.keyboardTheme.name
            prefs[keys.KEYBOARD_FONT] = settings.keyboardFont.name
            prefs[keys.KEY_BORDER] = settings.keyBorder
            prefs[keys.HORIZONTAL_GAP_SCALE] = settings.horizontalGapScale
            prefs[keys.VERTICAL_GAP_SCALE] = settings.verticalGapScale
            prefs[keys.KEY_LABEL_SCALE] = settings.keyLabelScale
            prefs[keys.SUGGESTION_BAR_SCALE] = settings.suggestionBarScale
            prefs[keys.BOTTOM_GAP_SCALE] = settings.bottomGapScale
            prefs[keys.KEYBOARD_HEIGHT_SCALE] = settings.keyboardHeightScale
            prefs[keys.KEY_PRESS_VIBRATION] = settings.keyPressVibration
            prefs[keys.KEY_PRESS_SOUND] = settings.keyPressSound
            prefs[keys.NUMBER_ROW_ENABLED] = settings.numberRowEnabled
            prefs[keys.SUGGESTIONS_ENABLED] = settings.suggestionsEnabled
            prefs[keys.AUTO_CORRECT_ENABLED] = settings.autoCorrectEnabled
            prefs[keys.BLOCK_OFFENSIVE_WORDS] = settings.blockOffensiveWords
            prefs[keys.CLIPBOARD_ENABLED] = settings.clipboardEnabled
            prefs[keys.AUTO_CAPITALIZATION] = settings.autoCapitalization
            prefs[keys.DOUBLE_SPACE_PERIOD] = settings.doubleSpacePeriod
            prefs[keys.BANGLA_AUTO_COMMIT] = settings.banglaAutoCommit
            prefs[keys.BANGLA_PHONETIC_SUGGESTIONS] = settings.banglaPhoneticSuggestions
            prefs[keys.LEARN_FROM_TYPING] = settings.learnFromTyping
            prefs[keys.VOLUME_KEY_CURSOR_CONTROL] = settings.volumeKeyCursorControl
        }

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
