package com.bornomala.keyboard.emoji.domain.repository

import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.emoji.domain.model.Emoji
import com.bornomala.keyboard.emoji.domain.model.EmojiCategory
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for emoji catalog access and usage tracking.
 *
 * Implemented in the data layer. Presentation depends only on this interface. All
 * catalog reads are cheap (in-memory after a lazy first load), while usage operations
 * touch Room off the main thread.
 *
 * Catalog loading is lazy and off the Main thread per the performance budget: callers
 * may invoke these freely from a `ViewModelScope` collector without blocking input.
 */
interface EmojiRepository {

    /**
     * Returns the bundled emoji for [category]. For [EmojiCategory.RECENT] callers
     * should use [observeRecent] instead, since that category is history-driven; this
     * function returns an empty list for it.
     */
    suspend fun emojisFor(category: EmojiCategory): AppResult<List<Emoji>>

    /**
     * Full-text-ish search over emoji names and keywords using a prebuilt index.
     * Matching is case-insensitive and prefix/substring based. An empty or blank
     * [query] yields an empty list (the UI shows categories instead).
     */
    suspend fun search(query: String): AppResult<List<Emoji>>

    /**
     * Observes the recent/frequently-used emoji, ordered by a recency-weighted
     * frequency score (most useful first). Emits a fresh list whenever usage history
     * changes. [limit] bounds the row length shown in the panel.
     */
    fun observeRecent(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<Emoji>>

    /**
     * Observes only the most frequently used emoji, ordered by raw usage count
     * (ties broken by recency). Backs the dedicated "frequent" row.
     */
    fun observeFrequent(limit: Int = DEFAULT_FREQUENT_LIMIT): Flow<List<Emoji>>

    /**
     * Records that the user inserted [emoji], incrementing its count and updating its
     * last-used timestamp. Safe to call from the input path; the write is dispatched
     * to I/O and never blocks the caller's critical section beyond enqueueing.
     */
    suspend fun recordUsage(emoji: Emoji): AppResult<Unit>

    /** Clears all persisted usage history (recent + frequent). */
    suspend fun clearUsage(): AppResult<Unit>

    companion object {
        const val DEFAULT_RECENT_LIMIT = 32
        const val DEFAULT_FREQUENT_LIMIT = 16
    }
}
