package com.bornomala.keyboard.clipboard.domain.model

import androidx.compose.runtime.Immutable

/**
 * A single entry in the keyboard clipboard history.
 *
 * Domain model exposed to presentation and other modules. It is deliberately decoupled
 * from the Room entity so the persistence schema can evolve without leaking into the UI
 * or callers. Marked [Immutable] so Compose can skip recomposition when the reference is
 * unchanged.
 *
 * @param id stable identifier (Room row id). `0` for a not-yet-persisted item.
 * @param text the copied text content.
 * @param pinned pinned items are exempt from the 100-item eviction cap.
 * @param createdAt epoch millis when the item was captured; drives ordering and eviction.
 */
@Immutable
data class ClipboardItem(
    val id: Long,
    val text: String,
    val pinned: Boolean,
    val createdAt: Long,
)
