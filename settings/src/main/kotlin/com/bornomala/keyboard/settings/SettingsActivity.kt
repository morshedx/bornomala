package com.bornomala.keyboard.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bornomala.keyboard.core.result.Resource
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.settings.presentation.SettingsScreen
import com.bornomala.keyboard.settings.presentation.SettingsViewModel
import com.bornomala.keyboard.theme.BornomalaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Launcher entry point for keyboard settings, referenced by the `:app` manifest's
 * launcher `<activity>` entry.
 *
 * The activity hoists a single [SettingsViewModel] so the persisted theme preference can
 * drive [BornomalaTheme] and the same instance backs [SettingsScreen] — keeping one
 * source of truth and avoiding a second DataStore subscription.
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val settings = (state as? Resource.Success)?.data ?: Settings.DEFAULTS

            BornomalaTheme(
                theme = settings.keyboardTheme,
                highContrast = settings.highContrast,
            ) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
