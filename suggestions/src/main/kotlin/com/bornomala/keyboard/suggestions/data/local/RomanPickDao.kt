package com.bornomala.keyboard.suggestions.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Data access for [RomanPickEntity]. All `suspend`; callers run on the I/O dispatcher. */
@Dao
interface RomanPickDao {

    @Query("SELECT word FROM roman_pick WHERE roman = :roman AND lang = :lang LIMIT 1")
    suspend fun find(roman: String, lang: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pick: RomanPickEntity)

    @Query("DELETE FROM roman_pick")
    suspend fun clear()

    // --- backup export / import ---------------------------------------------------------

    @Query("SELECT * FROM roman_pick")
    suspend fun getAll(): List<RomanPickEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(picks: List<RomanPickEntity>)

    @Transaction
    suspend fun replaceAll(picks: List<RomanPickEntity>) {
        clear()
        insertAll(picks)
    }
}
