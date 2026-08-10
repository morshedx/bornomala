package com.bornomala.keyboard.suggestions.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    entities = [UserDictionaryEntity::class, LearnedNgramEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class SuggestionsDatabase : RoomDatabase() {

    abstract fun userDictionaryDao(): UserDictionaryDao

    companion object {
        const val DATABASE_NAME = "bornomala_suggestions.db"

        /**
         * Adds the phonetic key that lets roman Avro input resolve to words the user taught the
         * keyboard. Migrated rather than dropped: learned words are the whole point of the
         * feature, so losing them on upgrade would be worse than the migration's cost. Existing
         * rows start with an empty key and are backfilled lazily by
         * [UserDictionaryRepository.backfillPhoneticKeys].
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_dictionary ADD COLUMN phonetic_key TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_user_dictionary_lang_phonetic_key_frequency " +
                        "ON user_dictionary (lang, phonetic_key, frequency)",
                )
            }
        }

        /**
         * Builds the database. Called lazily from DI on first use, never at IME
         * `onCreate`, so it stays off the cold-start path.
         */
        fun build(context: Context): SuggestionsDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SuggestionsDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_2_3)
                // Backstop only: a schema path with no migration drops the learned cache rather
                // than failing to open. Real upgrades ship a migration (see [MIGRATION_2_3]).
                .fallbackToDestructiveMigration()
                .build()
    }
}
