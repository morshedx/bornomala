package com.bornomala.keyboard.settings.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.core.result.Resource
import com.bornomala.keyboard.settings.R
import com.bornomala.keyboard.settings.data.fonts.GoogleFonts
import com.bornomala.keyboard.settings.domain.SettingsRepository
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.theme.KeyboardBackgroundImage
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardFontFile
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
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

    fun onAutoCorrectChange(enabled: Boolean) = launchEdit {
        repository.setAutoCorrectEnabled(enabled)
    }

    fun onBlockOffensiveWordsChange(enabled: Boolean) = launchEdit {
        repository.setBlockOffensiveWords(enabled)
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

    /**
     * Copies the photo the user picked into the app's private storage (off the main thread) and
     * switches the keyboard to it. Shows a short message if the image can't be read.
     */
    fun onBackgroundPhotoPicked(uri: Uri) = launchEdit {
        val saved = withContext(dispatchers.io) { KeyboardBackgroundImage.save(context, uri) }
        if (saved) {
            repository.setBackgroundImage(System.currentTimeMillis())
        } else {
            Toast.makeText(context, R.string.settings_photo_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun onBackgroundDimChange(dim: Float) = launchEdit { repository.setBackgroundDim(dim) }

    private val _fontBusy = MutableStateFlow<String?>(null)

    /** The Google Font being downloaded, [IMPORTING_FONT] while a file is copied in, else null. */
    val fontBusy: StateFlow<String?> = _fontBusy.asStateFlow()

    /** Downloads [family] through Play services, saves it and switches the keyboard to it. */
    fun onGoogleFontChosen(family: String) {
        if (_fontBusy.value != null) return
        launchEdit {
            _fontBusy.value = family
            val saved = try {
                withContext(dispatchers.io) { GoogleFonts.saveAsKeyboardFont(context, family) }
            } finally {
                _fontBusy.value = null
            }
            if (saved) {
                repository.setCustomFont(family, System.currentTimeMillis())
            } else {
                Toast.makeText(context, R.string.settings_font_download_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Copies the picked font file in (if it is a real font) and switches the keyboard to it. */
    fun onFontImported(uri: Uri) {
        if (_fontBusy.value != null) return
        launchEdit {
            _fontBusy.value = IMPORTING_FONT
            val (saved, name) = try {
                withContext(dispatchers.io) { KeyboardFontFile.import(context, uri) to displayName(uri) }
            } finally {
                _fontBusy.value = null
            }
            if (saved) {
                repository.setCustomFont(name, System.currentTimeMillis())
            } else {
                Toast.makeText(context, R.string.settings_font_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** The picked file's name without its extension, e.g. "Poppins-Regular". */
    private fun displayName(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty()
        return name.substringBeforeLast('.').ifEmpty { name }
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
