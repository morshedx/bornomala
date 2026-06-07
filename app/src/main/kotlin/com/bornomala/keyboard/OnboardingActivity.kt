package com.bornomala.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bornomala.keyboard.settings.SettingsActivity
import com.bornomala.keyboard.theme.BornomalaTheme

/**
 * Launcher entry point: a Gboard-style step-by-step activation flow shown until the keyboard
 * is enabled in system input settings and selected as the active input method. Once both are
 * done it shows a "ready" screen that opens the keyboard settings. Re-checks state on every
 * resume, so returning from system settings advances the steps automatically.
 */
class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BornomalaTheme {
                OnboardingScreen(
                    onEnableInSettings = {
                        startActivity(
                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    onSelectInputMethod = {
                        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                            .showInputMethodPicker()
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    onEnableInSettings: () -> Unit,
    onSelectInputMethod: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by remember { mutableStateOf(isImeEnabled(context)) }
    var selected by remember { mutableStateOf(isImeSelected(context)) }

    // Re-read activation state whenever we come back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = isImeEnabled(context)
        selected = isImeSelected(context)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(16.dp))

            when {
                !enabled -> Step(
                    title = "Step 1",
                    description = "Turn on Bornomala in your input settings.",
                    button = "Enable in settings",
                    onClick = onEnableInSettings,
                )

                !selected -> Step(
                    title = "Step 2",
                    description = "Select Bornomala as your input method.",
                    button = "Select input method",
                    onClick = onSelectInputMethod,
                )

                else -> Step(
                    title = "You're all set",
                    description = "Bornomala is ready. Tap a text field anywhere and switch with the 🌐 key.",
                    button = "Open settings",
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun Step(
    title: String,
    description: String,
    button: String,
    onClick: () -> Unit,
) {
    Text(text = title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    Button(onClick = onClick, modifier = Modifier.width(260.dp)) {
        Text(button)
    }
}

private fun isImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        ?: return false
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isImeSelected(context: Context): Boolean {
    val id = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD,
    ) ?: return false
    return id.startsWith(context.packageName + "/")
}
