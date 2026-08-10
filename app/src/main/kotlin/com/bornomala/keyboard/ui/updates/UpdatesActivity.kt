package com.bornomala.keyboard.ui.updates

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.text.font.FontFamily
import com.bornomala.keyboard.theme.BornomalaTheme
import dagger.hilt.android.AndroidEntryPoint
import im.morshed.ota.UpdateScreen

/**
 * Host activity for the OTA "Software update" screen. Launched from [com.bornomala.keyboard
 * .settings.SettingsActivity] when the user taps the Software Update entry in settings.
 *
 * The whole update flow — version check, download, and PackageInstaller hand-off — lives in the
 * `im.morshed:ota` library's [UpdateScreen]; this activity only hosts it inside the app theme and
 * supplies the back action. The library reads its manifest URL + token from the injected
 * [im.morshed.ota.OtaConfig] (see [com.bornomala.keyboard.di.OtaModule]).
 */
@AndroidEntryPoint
class UpdatesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BornomalaTheme(dynamicColor = true) {
                UpdateScreen(onBack = { finish() }, monoFont = FontFamily.Monospace)
            }
        }
    }
}
