package com.bornomala.keyboard.suggestions.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A word the user has typed/committed, with how often and when it was last used.
 *
 * The primary key is the composite (word, lang) so the same spelling can exist
 * independently per language without collision. [frequency] is a monotonically
 * increasing learning counter; [lastUsed] is epoch milliseconds used as a recency
 * tie-breaker and for future pruning of stale entries.
 *
 * The (lang, frequency) index backs the prefix + ranking query so lookups stay off
 * the main thread and cheap even as the dictionary grows.
 */
@Entity(
    tableName = "user_dictionary",
    primaryKeys = ["word", "lang"],
    indices = [
        Index(value = ["lang", "frequency"]),
        Index(value = ["lang", "prev_word"]),
        Index(value = ["lang", "phonetic_key", "frequency"]),
    ],
)
data class UserDictionaryEntity(
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "lang") val lang: String,
    @ColumnInfo(name = "frequency") val frequency: Int,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
    /**
     * The committed token that preceded [word] the last time it was learned, enabling
     * next-word prediction. Empty string when [word] started a sentence. Kept on the
     * same row (rather than a separate bigram table) to keep V1 storage minimal; a
     * dedicated bigram table is the natural future expansion.
     */
    @ColumnInfo(name = "prev_word") val prevWord: String = "",
    /**
     * The ambiguity-collapsed phonetic key of [word] (Bangla only; empty for English), so words
     * the user has taught the keyboard resolve from roman Avro input exactly like the bundled
     * phonetic index does. Computed on learn; backfilled lazily for rows written before the
     * column existed. See `BanglaPhoneticKey`.
     */
    @ColumnInfo(name = "phonetic_key") val phoneticKey: String = "",
)
