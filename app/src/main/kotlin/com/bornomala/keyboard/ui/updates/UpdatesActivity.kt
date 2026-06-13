package com.bornomala.keyboard.ui.updates

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bornomala.keyboard.theme.BornomalaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Host activity for the OTA "Software update" screen. Launched from [SettingsActivity]
 * when the user taps the Software Update entry in settings.
 */
@AndroidEntryPoint
class UpdatesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BornomalaTheme(dynamicColor = true) {
                UpdatesScreen(onBack = { finish() })
            }
        }
    }
}
