package com.bornomala.keyboard.clipboard.presentation

import androidx.compose.runtime.Immutable
import com.bornomala.keyboard.clipboard.domain.model.ClipboardItem

/**
 * Immutable UI state for the clipboard panel. A single state object keeps recomposition
 * scopes tight and lets the panel be driven by one `collectAsStateWithLifecycle`.
 *
 * @param items current (possibly filtered) history, ordered pinned-first then newest.
 * @param query active search query; empty means unfiltered.
 * @param isLoading true until the first emission from the repository arrives.
 */
@Immutable
data class ClipboardUiState(
    val items: List<ClipboardItem> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
) {
    /** No items match the current view (empty history or no search hits). */
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()

    /** Whether the empty state is due to a search miss rather than empty history. */
    val isSearchMiss: Boolean get() = isEmpty && query.isNotBlank()
}
