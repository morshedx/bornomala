package com.bornomala.keyboard.emoji.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted usage record for a single emoji glyph.
 *
 * The glyph itself is the primary key: there is exactly one row per distinct emoji,
 * which keeps the table tiny (bounded by catalog size) and makes increments a single
 * upsert. [count] drives the "frequent" ordering; [lastUsed] drives "recent" and acts
 * as the tie-breaker for frequency.
 */
@Entity(tableName = "emoji_usage")
data class EmojiUsageEntity(
    @PrimaryKey
    @ColumnInfo(name = "emoji")
    val emoji: String,

    /** Total number of times this emoji has been inserted. */
    @ColumnInfo(name = "count")
    val count: Int,

    /** Epoch-millis timestamp of the most recent insertion. */
    @ColumnInfo(name = "last_used")
    val lastUsed: Long,
)
