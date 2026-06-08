package com.bornomala.keyboard.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bornomala.keyboard.core.result.Resource
import com.bornomala.keyboard.settings.domain.SettingsRepository
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives [SettingsScreen]. Exposes the persisted [Settings] as a [Resource]-wrapped
 * [StateFlow] (Loading until the first DataStore emission) and forwards each toggle/edit
 * to [SettingsRepository] on the [viewModelScope].
 *
 * Writes are fire-and-forget from the UI's perspective: the new value is observed back
 * through the repository flow, giving a single source of truth and avoiding optimistic
 * local state that could drift from disk.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<Resource<Settings>> =
        repository.settings
            .map<Settings, Resource<Settings>> { Resource.Success(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = Resource.Loading,
            )

    fun onThemeModeChange(themeMode: ThemeMode) = launchEdit {
        repository.setThemeMode(themeMode)
    }

    fun onKeyboardThemeChange(theme: KeyboardTheme) = launchEdit {
        repository.setKeyboardTheme(theme)
    }

    fun onKeyboardFontChange(font: KeyboardFont) = launchEdit {
        repository.setKeyboardFont(font)
    }

    fun onKeyBorderChange(enabled: Boolean) = launchEdit { repository.setKeyBorder(enabled) }
    fun onHorizontalGapScaleChange(scale: Float) = launchEdit { repository.setHorizontalGapScale(scale) }
    fun onVerticalGapScaleChange(scale: Float) = launchEdit { repository.setVerticalGapScale(scale) }
    fun onKeyLabelScaleChange(scale: Float) = launchEdit { repository.setKeyLabelScale(scale) }
    fun onSuggestionBarScaleChange(scale: Float) = launchEdit { repository.setSuggestionBarScale(scale) }
    fun onBottomGapScaleChange(scale: Float) = launchEdit { repository.setBottomGapScale(scale) }

    fun onKeyboardHeightScaleChange(scale: Float) = launchEdit {
        repository.setKeyboardHeightScale(scale)
    }

    fun onKeyPressVibrationChange(enabled: Boolean) = launchEdit {
        repository.setKeyPressVibration(enabled)
    }

    fun onKeyPressSoundChange(enabled: Boolean) = launchEdit {
        repository.setKeyPressSound(enabled)
    }

    fun onNumberRowChange(enabled: Boolean) = launchEdit {
        repository.setNumberRowEnabled(enabled)
    }

    fun onSuggestionsChange(enabled: Boolean) = launchEdit {
        repository.setSuggestionsEnabled(enabled)
    }

    fun onClipboardChange(enabled: Boolean) = launchEdit {
        repository.setClipboardEnabled(enabled)
    }

    fun onAutoCapitalizationChange(enabled: Boolean) = launchEdit {
        repository.setAutoCapitalization(enabled)
    }

    fun onDoubleSpacePeriodChange(enabled: Boolean) = launchEdit {
        repository.setDoubleSpacePeriod(enabled)
    }

    fun onBanglaAutoCommitChange(enabled: Boolean) = launchEdit {
        repository.setBanglaAutoCommit(enabled)
    }

    fun onBanglaPhoneticSuggestionsChange(enabled: Boolean) = launchEdit {
        repository.setBanglaPhoneticSuggestions(enabled)
    }

    fun onLearnFromTypingChange(enabled: Boolean) = launchEdit {
        repository.setLearnFromTyping(enabled)
    }

    fun onVolumeKeyCursorControlChange(enabled: Boolean) = launchEdit {
        repository.setVolumeKeyCursorControl(enabled)
    }

    fun onResetToDefaults() = launchEdit {
        repository.resetToDefaults()
    }

    private inline fun launchEdit(crossinline block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
