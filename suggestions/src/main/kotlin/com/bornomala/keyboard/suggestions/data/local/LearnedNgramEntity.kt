package com.bornomala.keyboard.suggestions.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A learned next-word association: how often [word] followed a given [context], where the
 * context is the preceding word(s) joined by a single space.
 *
 * One table serves every order:
 *  - `context = "the"`        → a learned **bigram** (word follows "the"),
 *  - `context = "i want"`     → a learned **trigram** (word follows "i want").
 *
 * Unlike folding the previous word onto the word row (which keeps only the *last* context
 * and is therefore lossy), this keeps a real per-(context, word) frequency, so the same
 * word can be predicted from many different contexts with independent counts.
 *
 * Primary key (context, word, lang); the (lang, context, frequency) index backs the
 * ranked next-word query.
 */
@Entity(
    tableName = "learned_ngram",
    primaryKeys = ["context", "word", "lang"],
    indices = [Index(value = ["lang", "context", "frequency"])],
)
data class LearnedNgramEntity(
    @ColumnInfo(name = "context") val context: String,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "lang") val lang: String,
    @ColumnInfo(name = "frequency") val frequency: Int,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
)
