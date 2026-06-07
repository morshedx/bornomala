package com.bornomala.keyboard.clipboard.data.local

import com.bornomala.keyboard.clipboard.domain.model.ClipboardItem

/**
 * Pure mapping functions between the Room [ClipboardEntity] and the domain
 * [ClipboardItem]. Keeping these as top-level extensions avoids per-call allocation of a
 * mapper object and keeps the data layer's translation explicit.
 */
internal fun ClipboardEntity.toDomain(): ClipboardItem = ClipboardItem(
    id = id,
    text = text,
    pinned = pinned,
    createdAt = createdAt,
)

internal fun List<ClipboardEntity>.toDomain(): List<ClipboardItem> = map { it.toDomain() }
