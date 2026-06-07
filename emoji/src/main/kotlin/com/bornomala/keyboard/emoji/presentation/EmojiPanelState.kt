package com.bornomala.keyboard.emoji.presentation

import androidx.compose.runtime.Immutable
import com.bornomala.keyboard.emoji.domain.model.Emoji
import com.bornomala.keyboard.emoji.domain.model.EmojiCategory

/**
 * Immutable UI state for the [com.bornomala.keyboard.emoji.presentation.EmojiPanel].
 *
 * `@Immutable` lets Compose skip recomposition when an unchanged instance is passed,
 * which matters on the keyboard hot path. The grid renders either [searchResults] (when
 * [query] is non-blank), the [recent]/[frequent] rows plus the selected category grid.
 */
@Immutable
data class EmojiPanelState(
    val selectedCategory: EmojiCategory = EmojiCategory.RECENT,
    val query: String = "",
    /** Emoji of the currently selected static category (empty for RECENT). */
    val categoryEmojis: List<Emoji> = emptyList(),
    /** Recency-weighted recents, shown in the RECENT category and its row. */
    val recent: List<Emoji> = emptyList(),
    /** Pure-frequency favorites, shown in a dedicated row. */
    val frequent: List<Emoji> = emptyList(),
    /** Active when [query] is non-blank. */
    val searchResults: List<Emoji> = emptyList(),
) {
    val isSearching: Boolean get() = query.isNotBlank()

    /** True when there is genuinely nothing to display for the current view. */
    val isEmpty: Boolean
        get() = if (isSearching) {
            searchResults.isEmpty()
        } else if (selectedCategory.isDynamic) {
            recent.isEmpty() && frequent.isEmpty()
        } else {
            categoryEmojis.isEmpty()
        }
}
