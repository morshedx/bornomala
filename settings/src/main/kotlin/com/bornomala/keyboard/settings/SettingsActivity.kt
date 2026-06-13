package com.bornomala.keyboard.settings

import android.content.Intent
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
        // When launched from the in-keyboard settings menu, open directly on the chosen section.
        val initialSection = intent?.getStringExtra(EXTRA_SECTION)
        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val settings = (state as? Resource.Success)?.data ?: Settings.DEFAULTS

            BornomalaTheme(
                theme = settings.keyboardTheme,
                font = settings.keyboardFont,
                // Material You for the settings app UI (Android 12+); keyboard palette unchanged.
                dynamicColor = true,
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    initialSection = initialSection,
                    onSoftwareUpdate = {
                        // UpdatesActivity lives in :app and cannot be imported here; use class
                        // name so the :settings module stays free of :app dependencies.
                        startActivity(
                            Intent().setClassName(
                                packageName,
                                "com.bornomala.keyboard.ui.updates.UpdatesActivity",
                            ),
                        )
                    },
                )
            }
        }
    }

    companion object {
        /** Intent extra naming the settings section to open directly. Must match the IME's key. */
        const val EXTRA_SECTION = "com.bornomala.keyboard.SETTINGS_SECTION"
    }
}
