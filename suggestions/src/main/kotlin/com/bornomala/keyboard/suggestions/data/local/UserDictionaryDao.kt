package com.bornomala.keyboard.suggestions.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * Data access for the on-device user dictionary.
 *
 * All queries are `suspend` so callers run them on [DispatcherProvider.io]; none touch
 * the main thread. Learning uses an atomic UPSERT-by-increment ([learnWord]) so the
 * per-word frequency counter is race-free even under coalesced concurrent writes.
 */
@Dao
interface UserDictionaryDao {

    /**
     * Atomically learns [word]: inserts it with frequency 1 (or the given counts) or,
     * if it already exists for [lang], increments its frequency and refreshes recency
     * and the last-seen previous word. A single SQL statement so it is race-free.
     */
    @Query(
        """
        INSERT INTO user_dictionary (word, lang, frequency, last_used, prev_word)
        VALUES (:word, :lang, 1, :now, :prevWord)
        ON CONFLICT(word, lang) DO UPDATE SET
            frequency = frequency + 1,
            last_used = :now,
            prev_word = :prevWord
        """,
    )
    suspend fun learnWord(word: String, lang: String, prevWord: String, now: Long)

    /**
     * Current-word completion: rows in [lang] whose word starts with [prefix],
     * ranked by frequency then recency. [prefix] should already be lowercased by the
     * caller for English; Bangla is case-insensitive by nature.
     */
    @Query(
        """
        SELECT * FROM user_dictionary
        WHERE lang = :lang AND word LIKE :prefix || '%'
        ORDER BY frequency DESC, last_used DESC
        LIMIT :limit
        """,
    )
    suspend fun queryByPrefix(lang: String, prefix: String, limit: Int): List<UserDictionaryEntity>

    /**
     * Next-word prediction: words in [lang] most often learned immediately after
     * [previousWord], ranked by frequency then recency.
     */
    @Query(
        """
        SELECT * FROM user_dictionary
        WHERE lang = :lang AND prev_word = :previousWord
        ORDER BY frequency DESC, last_used DESC
        LIMIT :limit
        """,
    )
    suspend fun queryNextWord(lang: String, previousWord: String, limit: Int): List<UserDictionaryEntity>

    /** Exact lookup, used by tests and for confidence checks. Null when absent. */
    @Query("SELECT * FROM user_dictionary WHERE word = :word AND lang = :lang LIMIT 1")
    suspend fun findExact(word: String, lang: String): UserDictionaryEntity?

    /** Total learned entries for [lang]; supports tests and future pruning policy. */
    @Query("SELECT COUNT(*) FROM user_dictionary WHERE lang = :lang")
    suspend fun count(lang: String): Int

    /** Removes a learned word (e.g. user-initiated correction). */
    @Query("DELETE FROM user_dictionary WHERE word = :word AND lang = :lang")
    suspend fun delete(word: String, lang: String)

    /** Clears the entire user dictionary. Exposed for settings "reset learned words". */
    @Query("DELETE FROM user_dictionary")
    suspend fun clear()
}
