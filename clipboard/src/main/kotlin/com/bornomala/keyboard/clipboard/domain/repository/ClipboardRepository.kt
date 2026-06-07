package com.bornomala.keyboard.clipboard.domain.repository

import com.bornomala.keyboard.clipboard.domain.model.ClipboardItem
import com.bornomala.keyboard.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for clipboard history. Implemented in the data layer and consumed by
 * the presentation layer (ViewModel) and the `:keyboard` IME service.
 *
 * Invariants enforced by implementations:
 * - History is capped at [MAX_HISTORY_ITEMS]; when a new item pushes the count over the
 *   cap the oldest **non-pinned** item is evicted. Pinned items never count against the
 *   cap eviction and are never auto-removed.
 * - Ordering is pinned-first, then most-recent-first by `createdAt`.
 *
 * All writes return [AppResult] so callers handle storage failures explicitly without
 * exceptions crossing the architectural boundary. Reads are exposed as cold [Flow]s that
 * emit on every change.
 */
interface ClipboardRepository {

    /**
     * Observes the full history, ordered pinned-first then newest-first.
     * Emits a fresh list whenever the underlying data changes.
     */
    fun observeHistory(): Flow<List<ClipboardItem>>

    /**
     * Observes history filtered by [query] (case-insensitive substring match on text).
     * A blank query yields the full history. Ordering matches [observeHistory].
     */
    fun searchHistory(query: String): Flow<List<ClipboardItem>>

    /**
     * Captures [text] into history. Blank text is rejected with
     * [com.bornomala.keyboard.core.result.AppError.Validation]. If identical text already
     * exists it is refreshed (moved to the top) rather than duplicated. Enforces the
     * eviction cap after insert. Returns the row id of the stored item.
     */
    suspend fun addItem(text: String): AppResult<Long>

    /** Sets the pinned state of the item with [id]. */
    suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Unit>

    /** Permanently deletes the item with [id], regardless of pinned state. */
    suspend fun deleteItem(id: Long): AppResult<Unit>

    /** Deletes all non-pinned items. Pinned items are retained. */
    suspend fun clearUnpinned(): AppResult<Unit>

    companion object {
        /** Hard upper bound on retained history items (per SPEC). */
        const val MAX_HISTORY_ITEMS: Int = 100
    }
}
