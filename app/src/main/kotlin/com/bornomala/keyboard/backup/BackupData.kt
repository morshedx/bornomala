package com.bornomala.keyboard.backup

import com.bornomala.keyboard.clipboard.domain.model.ClipboardItem
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.suggestions.data.local.LearnedNgramEntity
import com.bornomala.keyboard.suggestions.data.local.RomanPickEntity
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryEntity

/**
 * Full snapshot of everything a backup carries: user settings, the learned dictionary
 * (words + n-grams + roman picks), and clipboard history, plus metadata. Serialized to JSON, then
 * encrypted with the user's passphrase before upload.
 */
data class BackupData(
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val device: String,
    val settings: Settings,
    val words: List<UserDictionaryEntity>,
    val ngrams: List<LearnedNgramEntity>,
    val clips: List<ClipboardItem>,
    /** Absent from archives written before roman picks existed; decodes as empty. */
    val romanPicks: List<RomanPickEntity> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/**
 * Lightweight remote-file info shown in the UI (no contents downloaded).
 *
 * @param encrypted the file is encrypted with a passphrase, which restore then needs.
 * @param legacy the pre-app-data backup in the visible `headquarter/bornomala` folder.
 */
data class BackupInfo(
    val fileId: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val encrypted: Boolean = false,
    val legacy: Boolean = false,
)
