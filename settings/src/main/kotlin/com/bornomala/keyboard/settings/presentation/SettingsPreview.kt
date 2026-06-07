package com.bornomala.keyboard.settings.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.theme.BornomalaTheme
import com.bornomala.keyboard.theme.ThemeMode

/**
 * Design-time previews of the stateless [SettingsContent]. A local mutable [Settings]
 * makes the controls interactive inside the preview without any ViewModel or DataStore.
 */
@Preview(name = "Settings - Light", showBackground = true)
@Composable
private fun SettingsContentLightPreview() {
    PreviewHost(ThemeMode.LIGHT)
}

@Preview(name = "Settings - Dark", showBackground = true)
@Composable
private fun SettingsContentDarkPreview() {
    PreviewHost(ThemeMode.DARK)
}

@Composable
private fun PreviewHost(themeMode: ThemeMode) {
    var settings by remember { mutableStateOf(Settings.DEFAULTS.copy(themeMode = themeMode)) }
    val callbacks = remember {
        SettingsCallbacks(
            onThemeMode = { settings = settings.copy(themeMode = it) },
            onKeyboardTheme = { settings = settings.copy(keyboardTheme = it) },
            onKeyboardFont = { settings = settings.copy(keyboardFont = it) },
            onHighContrast = { settings = settings.copy(highContrast = it) },
            onKeyboardHeightScale = { settings = settings.copy(keyboardHeightScale = it) },
            onVibration = { settings = settings.copy(keyPressVibration = it) },
            onSound = { settings = settings.copy(keyPressSound = it) },
            onNumberRow = { settings = settings.copy(numberRowEnabled = it) },
            onSuggestions = { settings = settings.copy(suggestionsEnabled = it) },
            onClipboard = { settings = settings.copy(clipboardEnabled = it) },
            onAutoCap = { settings = settings.copy(autoCapitalization = it) },
            onDoubleSpace = { settings = settings.copy(doubleSpacePeriod = it) },
            onBanglaAutoCommit = { settings = settings.copy(banglaAutoCommit = it) },
            onBanglaPhoneticSuggestions = { settings = settings.copy(banglaPhoneticSuggestions = it) },
            onLearnFromTyping = { settings = settings.copy(learnFromTyping = it) },
            onResetToDefaults = { settings = Settings.DEFAULTS.copy(themeMode = themeMode) },
        )
    }
    BornomalaTheme(themeMode = settings.themeMode, highContrast = settings.highContrast) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SettingsContent(settings = settings, callbacks = callbacks)
        }
    }
}
