package com.bornomala.keyboard.clipboard.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database owning the clipboard history table.
 *
 * Kept separate from other feature databases so the clipboard module stays self-contained
 * and its storage can be cleared independently (privacy: clearing clipboard must not touch
 * the user dictionary or emoji usage). Created lazily via Hilt on first use, never at IME
 * `onCreate`, to keep cold start under budget.
 */
@Database(
    entities = [ClipboardEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ClipboardDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao

    companion object {
        const val DATABASE_NAME = "bornomala_clipboard.db"
    }
}
