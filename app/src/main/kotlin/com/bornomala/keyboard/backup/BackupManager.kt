package com.bornomala.keyboard.backup

import android.content.Context
import android.os.Build
import com.bornomala.keyboard.backup.crypto.CryptoBox
import com.bornomala.keyboard.backup.drive.DriveClient
import com.bornomala.keyboard.clipboard.domain.repository.ClipboardRepository
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.settings.domain.SettingsRepository
import com.bornomala.keyboard.suggestions.data.local.RomanPickRepository
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/** A user-facing backup/restore failure carrying a message safe to show in the UI. */
class BackupException(message: String) : Exception(message)

/** Restore needs the passphrase of an encrypted backup, and none (or a wrong one) was given. */
class PassphraseRequiredException : Exception("This backup is protected with a passphrase. Enter it to restore.")

/**
 * Orchestrates a full backup/restore: gathers settings + learned dictionary + picks + clipboard,
 * serializes, and stores it in the hidden app-data folder of the user's Google Drive — as plain
 * JSON by default (protected by the Google account, like WhatsApp's default backup), or
 * encrypted with a passphrase when the user opts in. One-way (device → Drive → restore);
 * restore overwrites local data. No live sync / merge.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val userDictionary: UserDictionaryRepository,
    private val romanPicks: RomanPickRepository,
    private val clipboard: ClipboardRepository,
    private val drive: DriveClient,
    private val serializer: BackupSerializer,
) {
    /** The newest backup: the app-data file (plain or encrypted), or the legacy visible one. */
    suspend fun remoteInfo(token: String): BackupInfo? = newest(allBackups(token))

    /**
     * Snapshots everything and uploads it, replacing the previous app-data backup. With a
     * [passphrase] the file is encrypted first; without one it is stored as-is. The legacy
     * visible backup is never touched, so an old passphrase can still unlock it later.
     */
    suspend fun backUp(token: String, passphrase: CharArray?) {
        val settings = settingsRepository.settings.first()
        val (words, ngrams) = userDictionary.exportAll().orThrow()
        val clips = clipboard.exportAll().orThrow()
        val picks = romanPicks.exportAll().orThrow()
        val data = BackupData(
            schemaVersion = BackupData.SCHEMA_VERSION,
            appVersion = appVersion(),
            createdAt = System.currentTimeMillis(),
            device = Build.MODEL ?: "Android device",
            settings = settings,
            words = words,
            ngrams = ngrams,
            clips = clips,
            romanPicks = picks,
        )
        val json = serializer.encode(data)
        val (name, other, bytes) = if (passphrase != null) {
            Triple(DriveClient.ENCRYPTED_FILE, DriveClient.PLAIN_FILE, CryptoBox.encrypt(json, passphrase))
        } else {
            Triple(DriveClient.PLAIN_FILE, DriveClient.ENCRYPTED_FILE, json)
        }
        drive.uploadAppData(token, name, bytes, existingId = drive.findAppData(token, name)?.fileId)
        // Keep a single app-data backup: drop the other kind if the user switched modes.
        drive.findAppData(token, other)?.let { drive.delete(token, it.fileId) }
    }

    /**
     * Restores the newest backup, overwriting local settings, dictionary, picks and clipboard.
     * An encrypted backup needs its [passphrase]; without it (or with a wrong one) this throws
     * [PassphraseRequiredException].
     */
    suspend fun restore(token: String, passphrase: CharArray?) {
        val info = remoteInfo(token)
            ?: throw BackupException("No backup found in your Google Drive")
        val blob = drive.download(token, info.fileId)
        val json = if (info.encrypted) {
            if (passphrase == null) throw PassphraseRequiredException()
            try {
                CryptoBox.decrypt(blob, passphrase)
            } catch (_: AEADBadTagException) {
                throw BackupException("Wrong passphrase, or the backup is corrupted")
            }
        } else {
            blob
        }
        val data = serializer.decode(json)
        settingsRepository.replaceAll(data.settings).orThrow()
        userDictionary.replaceAll(data.words, data.ngrams).orThrow()
        romanPicks.replaceAll(data.romanPicks).orThrow()
        clipboard.replaceAll(data.clips).orThrow()
    }

    /** Deletes every backup from Drive — app-data and legacy (no-op if none). */
    suspend fun deleteRemote(token: String) {
        for (info in allBackups(token)) drive.delete(token, info.fileId)
    }

    private suspend fun allBackups(token: String): List<BackupInfo> = listOfNotNull(
        drive.findAppData(token, DriveClient.PLAIN_FILE),
        drive.findAppData(token, DriveClient.ENCRYPTED_FILE),
        drive.findLegacy(token),
    )

    private fun appVersion(): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"

    private fun <T> AppResult<T>.orThrow(): T = when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> throw BackupException(error.message)
    }
}

/** The most recently modified backup, or null when there is none. */
internal fun newest(backups: List<BackupInfo>): BackupInfo? = backups.maxByOrNull { it.modifiedAtMillis }
