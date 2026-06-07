package com.bornomala.keyboard.emoji.domain.model

import androidx.compose.runtime.Immutable

/**
 * The fixed set of emoji categories presented as tabs in the [EmojiPanel].
 *
 * [RECENT] and [SMILEYS]… map to the curated bundled catalog, while [RECENT] is a
 * dynamic, usage-driven category populated from persisted usage history rather than
 * the static catalog. The ordinal order is the tab order shown to the user.
 */
@Immutable
enum class EmojiCategory(
    /** Stable identifier persisted/serialized; never localized. */
    val id: String,
) {
    /** Dynamic: recently and frequently used emoji from usage history. */
    RECENT("recent"),
    SMILEYS("smileys"),
    PEOPLE("people"),
    ANIMALS("animals"),
    FOOD("food"),
    ACTIVITY("activity"),
    TRAVEL("travel"),
    OBJECTS("objects"),
    SYMBOLS("symbols"),
    FLAGS("flags"),
    ;

    /** True for the synthetic, history-backed category that has no static entries. */
    val isDynamic: Boolean get() = this == RECENT

    companion object {
        /** Categories backed by the static bundled catalog (excludes [RECENT]). */
        val staticCategories: List<EmojiCategory> = entries.filter { !it.isDynamic }

        fun fromId(id: String): EmojiCategory? = entries.firstOrNull { it.id == id }
    }
}
