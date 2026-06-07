package com.bornomala.keyboard.suggestions.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database owned by the :suggestions module.
 *
 * Design choice: each feature module (suggestions, clipboard, emoji) owns its own
 * Room database file rather than sharing one. This keeps modules decoupled (no shared
 * schema/migration coordination), lets each DB be lazily created on first real use to
 * protect IME cold start, and matches the Clean-Architecture module boundary. The
 * tradeoff (a few extra small SQLite files) is negligible for this workload.
 */
@Database(
    entities = [UserDictionaryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SuggestionsDatabase : RoomDatabase() {

    abstract fun userDictionaryDao(): UserDictionaryDao

    companion object {
        const val DATABASE_NAME = "bornomala_suggestions.db"

        /**
         * Builds the database. Called lazily from DI on first use, never at IME
         * `onCreate`, so it stays off the cold-start path.
         */
        fun build(context: Context): SuggestionsDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SuggestionsDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
