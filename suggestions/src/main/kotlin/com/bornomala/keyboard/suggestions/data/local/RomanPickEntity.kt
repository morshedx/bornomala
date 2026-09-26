package com.bornomala.keyboard.suggestions.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * The Bangla word the user last picked from the suggestion strip for an exact roman spelling —
 * e.g. `bus` -> বাস. Lets an explicit choice become the auto-pick for that spelling next time,
 * which the phonetic-key learning in `user_dictionary` cannot express for loanwords (the key of
 * বাস never matches the roman `bus`).
 *
 * One row per (roman, lang): the latest pick replaces the previous one.
 */
@Entity(tableName = "roman_pick", primaryKeys = ["roman", "lang"])
data class RomanPickEntity(
    @ColumnInfo(name = "roman") val roman: String,
    @ColumnInfo(name = "lang") val lang: String,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
)
