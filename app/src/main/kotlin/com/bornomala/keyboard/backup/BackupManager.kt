package com.bornomala.keyboard.backup

import android.content.Context
import android.os.Build
import com.bornomala.keyboard.backup.drive.DriveClient
import com.bornomala.keyboard.clipboard.domain.repository.ClipboardRepository
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.settings.domain.SettingsRepository
import com.bornomala.keyboard.suggestions.data.local.RomanPickRepository
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** A user-facing backup/restore failure carrying a message safe to show in the UI. */
class BackupException(message: String) : Exception(message)

/**
 * Orchestrates a full backup/restore: gathers settings + learned dictionary + picks + clipboard,
 * serializes them to JSON, and stores that in the hidden app-data folder of the user's Google
 * Drive, protected by their Google account (like WhatsApp's backup). One-way (device → Drive →
 * restore); restore overwrites local data. No live sync / merge.
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
    /** The backup in Drive, or null when there is none. */
    suspend fun remoteInfo(token: String): BackupInfo? = drive.findAppData(token, DriveClient.BACKUP_FILE)

    /** Snapshots everything and uploads it, replacing the previous backup. */
    suspend fun backUp(token: String) {
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
        val existing = drive.findAppData(token, DriveClient.BACKUP_FILE)?.fileId
        drive.uploadAppData(token, DriveClient.BACKUP_FILE, json, existingId = existing)
        deleteOldEncrypted(token)
    }

    /** Restores the backup, overwriting local settings, dictionary, picks and clipboard. */
    suspend fun restore(token: String) {
        val info = remoteInfo(token)
            ?: throw BackupException("No backup found in your Google Drive")
        val data = serializer.decode(drive.download(token, info.fileId))
        settingsRepository.replaceAll(data.settings).orThrow()
        userDictionary.replaceAll(data.words, data.ngrams).orThrow()
        romanPicks.replaceAll(data.romanPicks).orThrow()
        clipboard.replaceAll(data.clips).orThrow()
    }

    /** Deletes the backup from Drive (no-op if none). */
    suspend fun deleteRemote(token: String) {
        drive.findAppData(token, DriveClient.BACKUP_FILE)?.let { drive.delete(token, it.fileId) }
        deleteOldEncrypted(token)
    }

    /** Removes a passphrase-encrypted backup left by an older version; nothing can read it now. */
    private suspend fun deleteOldEncrypted(token: String) {
        drive.findAppData(token, DriveClient.OLD_ENCRYPTED_FILE)?.let { drive.delete(token, it.fileId) }
    }

    private fun appVersion(): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"

    private fun <T> AppResult<T>.orThrow(): T = when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> throw BackupException(error.message)
    }
}
