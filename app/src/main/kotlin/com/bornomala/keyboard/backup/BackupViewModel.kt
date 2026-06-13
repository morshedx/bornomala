package com.bornomala.keyboard.backup

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bornomala.keyboard.backup.drive.GoogleAuthManager
import com.bornomala.keyboard.backup.drive.GoogleAuthManager.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val signedIn: Boolean = false,
    val email: String? = null,
    val lastBackupAt: Long? = null,
    val remoteSizeBytes: Long? = null,
    val autoEnabled: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val backupManager: BackupManager,
    private val store: BackupStore,
    private val scheduler: BackupScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(
        BackupUiState(
            signedIn = store.signedIn,
            email = store.email,
            lastBackupAt = store.lastBackupAt.takeIf { it > 0 },
            autoEnabled = store.autoEnabled,
        ),
    )
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Emits a consent intent the Activity must launch to complete first-time sign-in. */
    private val _consent = Channel<PendingIntent>(Channel.BUFFERED)
    val consent = _consent.receiveAsFlow()

    private var token: String? = null

    fun signIn() = launchBusy {
        when (val s = authManager.authorize()) {
            is AuthState.Authorized -> onAuthorized(s)
            is AuthState.NeedsConsent -> _consent.send(s.pendingIntent)
        }
    }

    fun onConsentResult(data: Intent) = launchBusy {
        onAuthorized(authManager.resultFromIntent(data))
        refreshRemote()
    }

    fun onConsentCancelled() = _state.update { it.copy(busy = false, message = "Sign-in cancelled") }

    fun backupNow(passphrase: String) = withPassphrase(passphrase) { pass ->
        val t = ensureToken() ?: return@withPassphrase
        backupManager.backUp(t, pass)
        store.savePassphrase(pass)
        store.lastBackupAt = System.currentTimeMillis()
        refreshFromStore("Backed up to Google Drive")
        refreshRemote()
    }

    fun restore(passphrase: String) = withPassphrase(passphrase) { pass ->
        val t = ensureToken() ?: return@withPassphrase
        backupManager.restore(t, pass)
        store.savePassphrase(pass)
        refreshFromStore("Restored. Reopen the keyboard if it doesn't refresh.")
    }

    fun setAuto(enabled: Boolean) = launchBusy {
        if (enabled && !store.hasPassphrase) {
            _state.update { it.copy(message = "Back up once first to enable auto-backup") }
            return@launchBusy
        }
        store.autoEnabled = enabled
        if (enabled) scheduler.enableDaily() else scheduler.disable()
        refreshFromStore(null)
    }

    fun deleteBackup() = launchBusy {
        val t = ensureToken() ?: return@launchBusy
        backupManager.deleteRemote(t)
        refreshFromStore("Backup deleted from Drive")
        _state.update { it.copy(remoteSizeBytes = null) }
    }

    fun signOut() {
        store.clear()
        scheduler.disable()
        token = null
        _state.value = BackupUiState(message = "Signed out")
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // --- internals --------------------------------------------------------------------

    private fun onAuthorized(a: AuthState.Authorized) {
        token = a.accessToken
        store.email = a.email ?: store.email ?: "Google account"
        refreshFromStore("Signed in")
    }

    private suspend fun ensureToken(): String? {
        token?.let { return it }
        return when (val s = authManager.authorize()) {
            is AuthState.Authorized -> s.accessToken.also { token = it }
            is AuthState.NeedsConsent -> { _consent.send(s.pendingIntent); null }
        }
    }

    private suspend fun refreshRemote() {
        val t = token ?: return
        runCatching { backupManager.remoteInfo(t) }.getOrNull()?.let { info ->
            _state.update {
                it.copy(
                    remoteSizeBytes = info.sizeBytes,
                    lastBackupAt = info.modifiedAtMillis.takeIf { m -> m > 0 } ?: it.lastBackupAt,
                )
            }
        }
    }

    private fun refreshFromStore(message: String?) = _state.update {
        it.copy(
            signedIn = store.signedIn,
            email = store.email,
            autoEnabled = store.autoEnabled,
            lastBackupAt = store.lastBackupAt.takeIf { v -> v > 0 } ?: it.lastBackupAt,
            message = message ?: it.message,
        )
    }

    private inline fun withPassphrase(passphrase: String, crossinline block: suspend (CharArray) -> Unit) {
        if (passphrase.length < MIN_PASSPHRASE) {
            _state.update { it.copy(message = "Use a passphrase of at least $MIN_PASSPHRASE characters") }
            return
        }
        launchBusy {
            val pass = passphrase.toCharArray()
            try {
                block(pass)
            } finally {
                pass.fill(' ')
            }
        }
    }

    private inline fun launchBusy(crossinline block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "Something went wrong") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val MIN_PASSPHRASE = 6
    }
}
