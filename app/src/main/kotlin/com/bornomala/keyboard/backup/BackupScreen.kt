package com.bornomala.keyboard.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.bornomala.keyboard.settings.presentation.components.SettingsPage
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bornomala.keyboard.theme.LucideIcons
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(state.message) {
        state.message?.let {
            scope.launch { snackbar.showSnackbar(it) }
            viewModel.consumeMessage()
        }
    }

    SettingsPage(
        title = "Backup & restore",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            Text(
                "Back up your settings, learned words and clipboard to your Google Drive, and " +
                    "restore them on a new phone. The backup is stored privately in your Drive " +
                    "and protected by your Google account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (!state.signedIn) {
                Button(
                    onClick = viewModel::signIn,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign in with Google") }
                return@Column
            }

            Text(
                "Account",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                state.email ?: "Signed in",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
            )
            Text(
                lastBackupLabel(state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = viewModel::backupNow,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Back up now") }
                OutlinedButton(
                    onClick = viewModel::restore,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Restore") }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic backup", fontWeight = FontWeight.Medium)
                    Text(
                        "Daily backup when charging on Wi-Fi (after one backup).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.autoEnabled,
                    onCheckedChange = viewModel::setAuto,
                    enabled = !state.busy,
                )
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = viewModel::deleteBackup, enabled = !state.busy) {
                Text("Delete backup from Drive", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = viewModel::signOut, enabled = !state.busy) {
                Text("Sign out")
            }
        }
    }
}

private fun lastBackupLabel(state: BackupUiState): String {
    val at = state.lastBackupAt ?: return "No backup yet"
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))
    val size = state.remoteSizeBytes?.let { " · ${it / 1024} KB" } ?: ""
    return "Last backup: $date$size"
}
