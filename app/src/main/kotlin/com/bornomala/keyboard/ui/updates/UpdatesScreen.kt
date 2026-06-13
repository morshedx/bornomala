package com.bornomala.keyboard.ui.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bornomala.keyboard.data.update.UpdateManifest
import com.bornomala.keyboard.data.update.UpdateStatus
import com.bornomala.keyboard.theme.LucideIcons
import com.bornomala.keyboard.util.ApkInstaller

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onBack: () -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Software update") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(LucideIcons.ArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status emblem
            val checking = state.status is UpdateStatus.Checking
            val available = state.status is UpdateStatus.Available
            val emblem = when {
                available -> LucideIcons.Sparkles
                state.status is UpdateStatus.Error -> LucideIcons.TriangleAlert
                else -> LucideIcons.CircleCheck
            }
            val emblemColor = when {
                available -> MaterialTheme.colorScheme.primary
                state.status is UpdateStatus.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(emblemColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                } else {
                    Icon(emblem, contentDescription = null, tint = emblemColor, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            Text(headline(state), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Installed: v${state.versionName} · ${state.buildType}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Body(
                        state = state,
                        onCheck = viewModel::checkForUpdate,
                        onDownload = viewModel::download,
                        onInstall = {
                            val file = state.readyFile ?: return@Body
                            if (ApkInstaller.canInstall(context)) ApkInstaller.install(context, file)
                            else ApkInstaller.requestInstallPermission(context)
                        },
                    )
                }
            }

            // Changelog / what's-new card for the latest version (shown whether up to date or not).
            val notesManifest = statusManifest(state.status)
            if (notesManifest != null && notesManifest.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "What's new in v${notesManifest.versionName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            notesManifest.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun statusManifest(s: UpdateStatus): UpdateManifest? = when (s) {
    is UpdateStatus.Available -> s.manifest
    is UpdateStatus.UpToDate -> s.manifest
    else -> null
}

private fun headline(state: UpdatesUiState): String = when (state.status) {
    is UpdateStatus.Available -> "Update available"
    UpdateStatus.Checking -> "Checking…"
    is UpdateStatus.UpToDate -> "You're up to date"
    is UpdateStatus.Error -> "Couldn't check"
    UpdateStatus.Idle -> "Software update"
}

@Composable
private fun Body(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    onDownload: (UpdateManifest) -> Unit,
    onInstall: () -> Unit,
) {
    if (!state.updatesConfigured) {
        Text(
            "Update source not configured yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    when (val status = state.status) {
        is UpdateStatus.Available -> {
            Text(
                "Version ${status.manifest.versionName} is ready to install",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            when {
                state.readyFile != null -> Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Icon(LucideIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Install now")
                }
                state.downloading -> {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Downloading… ${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Button(onClick = { onDownload(status.manifest) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(LucideIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Download & install")
                }
            }
        }

        UpdateStatus.Checking -> Text(
            "Looking for a newer version…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is UpdateStatus.UpToDate -> {
            Text(
                "You have the latest version installed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                Icon(LucideIcons.RefreshCw, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Check again")
            }
        }

        is UpdateStatus.Error -> {
            Text(status.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
        }

        UpdateStatus.Idle -> Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
            Text("Check for updates")
        }
    }
}
