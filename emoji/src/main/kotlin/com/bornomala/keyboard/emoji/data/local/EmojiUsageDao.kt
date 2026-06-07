package com.bornomala.keyboard.emoji.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for emoji usage history.
 *
 * Recency-weighted ordering for "recent" and pure-count ordering for "frequent" are
 * both computed in SQL so the database does the sorting (no in-memory re-sorting on
 * the hot path). The usage increment is an atomic upsert expressed as a single
 * `INSERT … ON CONFLICT` statement to avoid a read-modify-write round trip.
 */
@Dao
interface EmojiUsageDao {

    /**
     * Atomic upsert: inserts a new row with count 1, or increments the existing row's
     * count and refreshes its [EmojiUsageEntity.lastUsed]. Single statement, single
     * disk touch.
     */
    @Query(
        """
        INSERT INTO emoji_usage (emoji, count, last_used)
        VALUES (:emoji, 1, :now)
        ON CONFLICT(emoji) DO UPDATE SET
            count = count + 1,
            last_used = :now
        """,
    )
    suspend fun upsertUsage(emoji: String, now: Long)

    /**
     * Recent emoji ordered by recency first, then frequency. Drives the "recent" row
     * and the dynamic RECENT category.
     */
    @Query(
        """
        SELECT * FROM emoji_usage
        ORDER BY last_used DESC, count DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<EmojiUsageEntity>>

    /**
     * Most frequently used emoji ordered by raw count, ties broken by recency.
     * Drives the dedicated "frequent" row.
     */
    @Query(
        """
        SELECT * FROM emoji_usage
        ORDER BY count DESC, last_used DESC
        LIMIT :limit
        """,
    )
    fun observeFrequent(limit: Int): Flow<List<EmojiUsageEntity>>

    @Query("DELETE FROM emoji_usage")
    suspend fun clear()
}
