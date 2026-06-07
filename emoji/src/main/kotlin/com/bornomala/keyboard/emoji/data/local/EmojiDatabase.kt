package com.bornomala.keyboard.emoji.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database owned by the :emoji module.
 *
 * Shared-vs-own DB decision: :emoji keeps its **own** database rather than
 * contributing entities to a shared app DB. Its single [EmojiUsageEntity] table has no
 * foreign-key relationships with other modules' data (clipboard, user dictionary), and
 * an isolated DB keeps the module self-contained, independently testable, and free of
 * cross-module migration coupling. The file is small and opened lazily on first use.
 */
@Database(
    entities = [EmojiUsageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EmojiDatabase : RoomDatabase() {
    abstract fun emojiUsageDao(): EmojiUsageDao

    companion object {
        const val DATABASE_NAME = "bornomala_emoji.db"
    }
}
