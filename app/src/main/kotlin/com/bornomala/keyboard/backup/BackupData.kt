package com.bornomala.keyboard.backup

import com.bornomala.keyboard.clipboard.domain.model.ClipboardItem
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.suggestions.data.local.LearnedNgramEntity
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryEntity

/**
 * Full snapshot of everything a backup carries: user settings, the learned dictionary
 * (words + n-grams), and clipboard history, plus metadata. Serialized to JSON, then
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
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** Lightweight remote-file info shown in the UI (no contents downloaded). */
data class BackupInfo(
    val fileId: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
)
