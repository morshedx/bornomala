package com.bornomala.keyboard.clipboard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for clipboard history.
 *
 * Ordering convention used everywhere: `pinned DESC` (pinned first) then
 * `created_at DESC` (newest first).
 *
 * Eviction strategy: after each insert, [addAndEvict] trims non-pinned rows so the total
 * row count never exceeds the cap. Pinned rows are excluded from the trim entirely, so a
 * history full of pinned items will never auto-delete and may legitimately exceed the cap
 * for non-pinned headroom purposes (the cap governs eviction of unpinned items only).
 */
@Dao
abstract class ClipboardDao {

    @Query(
        "SELECT * FROM clipboard_items " +
            "ORDER BY pinned DESC, created_at DESC",
    )
    abstract fun observeAll(): Flow<List<ClipboardEntity>>

    /**
     * Case-insensitive substring search. Uses `LIKE` with `%` wrappers bound as a
     * parameter; callers pass the already-escaped pattern. Same ordering as [observeAll].
     */
    @Query(
        "SELECT * FROM clipboard_items " +
            "WHERE text LIKE :pattern ESCAPE '\\' " +
            "ORDER BY pinned DESC, created_at DESC",
    )
    abstract fun search(pattern: String): Flow<List<ClipboardEntity>>

    @Query("SELECT * FROM clipboard_items WHERE text = :text LIMIT 1")
    abstract suspend fun findByText(text: String): ClipboardEntity?

    @Query("SELECT * FROM clipboard_items WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: Long): ClipboardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: ClipboardEntity): Long

    @Query("UPDATE clipboard_items SET pinned = :pinned WHERE id = :id")
    abstract suspend fun updatePinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM clipboard_items WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM clipboard_items WHERE pinned = 0")
    abstract suspend fun deleteAllUnpinned()

    @Query("SELECT COUNT(*) FROM clipboard_items WHERE pinned = 0")
    abstract suspend fun countUnpinned(): Int

    /**
     * Deletes the oldest non-pinned rows so that at most [maxUnpinned] non-pinned rows
     * remain. No-op when already within budget. Pinned rows are untouched.
     */
    @Query(
        "DELETE FROM clipboard_items WHERE id IN (" +
            "SELECT id FROM clipboard_items WHERE pinned = 0 " +
            "ORDER BY created_at DESC " +
            "LIMIT -1 OFFSET :maxUnpinned" +
            ")",
    )
    abstract suspend fun evictOverflow(maxUnpinned: Int)

    /**
     * Inserts (or refreshes) [entity] then enforces the eviction cap atomically.
     *
     * Runs in a single transaction so observers never see an over-capacity intermediate
     * state. Returns the row id of the inserted/updated item.
     */
    @Transaction
    open suspend fun addAndEvict(entity: ClipboardEntity, maxUnpinned: Int): Long {
        val existing = findByText(entity.text)
        val toStore = if (existing != null) {
            // Refresh in place: keep id and existing pin state, bump the timestamp so the
            // item floats back to the top.
            entity.copy(id = existing.id, pinned = existing.pinned)
        } else {
            entity
        }
        val rowId = upsert(toStore)
        evictOverflow(maxUnpinned)
        return if (existing != null) existing.id else rowId
    }
}
