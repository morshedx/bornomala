package com.bornomala.keyboard.backup

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.bornomala.keyboard.theme.BornomalaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Host for the cloud Backup & restore screen. Launched from Settings. Owns the Google
 * consent flow: the ViewModel emits a consent [android.app.PendingIntent] on first sign-in,
 * which this activity launches via the IntentSender contract and feeds the result back.
 */
@AndroidEntryPoint
class BackupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: BackupViewModel = hiltViewModel()
            val consentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                val data = result.data
                if (result.resultCode == Activity.RESULT_OK && data != null) {
                    viewModel.onConsentResult(data)
                } else {
                    viewModel.onConsentCancelled()
                }
            }
            LaunchedEffect(Unit) {
                viewModel.consent.collect { pendingIntent ->
                    consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                }
            }
            BornomalaTheme(dynamicColor = true) {
                BackupScreen(onBack = { finish() }, viewModel = viewModel)
            }
        }
    }
}
