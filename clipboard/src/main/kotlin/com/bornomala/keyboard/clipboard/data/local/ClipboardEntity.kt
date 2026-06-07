package com.bornomala.keyboard.clipboard.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a clipboard history row.
 *
 * Indices:
 * - unique index on [text] supports de-duplication (refresh instead of insert duplicate).
 * - composite index on ([pinned], [createdAt]) backs the pinned-first / newest-first
 *   ordering and the eviction lookup of the oldest non-pinned row.
 */
@Entity(
    tableName = "clipboard_items",
    indices = [
        Index(value = ["text"], unique = true),
        Index(value = ["pinned", "created_at"]),
    ],
)
data class ClipboardEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "pinned")
    val pinned: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
