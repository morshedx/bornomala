package com.bornomala.keyboard.backup

import android.content.Context
import android.os.Build
import com.bornomala.keyboard.backup.crypto.CryptoBox
import com.bornomala.keyboard.backup.drive.DriveClient
import com.bornomala.keyboard.clipboard.domain.repository.ClipboardRepository
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.settings.domain.SettingsRepository
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/** A user-facing backup/restore failure carrying a message safe to show in the UI. */
class BackupException(message: String) : Exception(message)

/**
 * Orchestrates a full backup/restore: gathers settings + learned dictionary + clipboard,
 * serializes, encrypts with the user's passphrase, and stores a single visible file in the
 * user's Google Drive (`drive.file`). One-way (device → Drive → restore); restore overwrites
 * local data. No live sync / merge.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val userDictionary: UserDictionaryRepository,
    private val clipboard: ClipboardRepository,
    private val drive: DriveClient,
    private val serializer: BackupSerializer,
) {
    suspend fun remoteInfo(token: String): BackupInfo? = drive.findBackup(token)

    /** Snapshots everything, encrypts it, and uploads (overwriting any existing backup). */
    suspend fun backUp(token: String, passphrase: CharArray) {
        val settings = settingsRepository.settings.first()
        val (words, ngrams) = userDictionary.exportAll().orThrow()
        val clips = clipboard.exportAll().orThrow()
        val data = BackupData(
            schemaVersion = BackupData.SCHEMA_VERSION,
            appVersion = appVersion(),
            createdAt = System.currentTimeMillis(),
            device = Build.MODEL ?: "Android device",
            settings = settings,
            words = words,
            ngrams = ngrams,
            clips = clips,
        )
        val blob = CryptoBox.encrypt(serializer.encode(data), passphrase)
        drive.upload(token, blob, existingId = drive.findBackup(token)?.fileId)
    }

    /** Downloads, decrypts, and overwrites local settings + dictionary + clipboard. */
    suspend fun restore(token: String, passphrase: CharArray) {
        val info = drive.findBackup(token)
            ?: throw BackupException("No backup found in your Google Drive")
        val blob = drive.download(token, info.fileId)
        val plain = try {
            CryptoBox.decrypt(blob, passphrase)
        } catch (_: AEADBadTagException) {
            throw BackupException("Wrong passphrase, or the backup is corrupted")
        }
        val data = serializer.decode(plain)
        settingsRepository.replaceAll(data.settings).orThrow()
        userDictionary.replaceAll(data.words, data.ngrams).orThrow()
        clipboard.replaceAll(data.clips).orThrow()
    }

    /** Deletes the backup file from Drive (no-op if none). */
    suspend fun deleteRemote(token: String) {
        val id = drive.findBackup(token)?.fileId ?: return
        drive.delete(token, id)
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
