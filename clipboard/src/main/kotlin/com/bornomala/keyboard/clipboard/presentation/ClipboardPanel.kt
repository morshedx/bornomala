package com.bornomala.keyboard.clipboard.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.bornomala.keyboard.clipboard.domain.model.ClipboardItem
import com.bornomala.keyboard.theme.BornomalaTheme

/**
 * Clipboard history panel surfaced from the keyboard. Lets the user search, pin/unpin,
 * delete, and tap an item to paste it into the active editor.
 *
 * Stateless rendering is delegated to [ClipboardPanelContent]; this overload binds the
 * Hilt [ClipboardViewModel] and is the entry point used by the `:keyboard` IME view.
 *
 * @param onPaste invoked with the item's text when the user taps a row; the IME service
 *   commits it to the [android.view.inputmethod.InputConnection].
 */
@Composable
fun ClipboardPanel(
    onPaste: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClipboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ClipboardPanelContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onPaste = onPaste,
        onTogglePin = viewModel::onTogglePin,
        onDelete = viewModel::onDelete,
        modifier = modifier,
    )
}

/**
 * Stateless clipboard panel. Separated from the ViewModel-bound overload so it can be
 * previewed and UI-tested without Hilt.
 */
@Composable
fun ClipboardPanelContent(
    state: ClipboardUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onPaste: (String) -> Unit,
    onTogglePin: (id: Long, currentlyPinned: Boolean) -> Unit,
    onDelete: (id: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BornomalaTheme.keyboardColors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp),
        color = colors.keyboardBackground,
        contentColor = colors.keyContent,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // No in-panel search field: an IME cannot type into its own UI without a
            // sub-keyboard. `onQueryChange`/`onClearQuery` are unused here.
            when {
                state.isLoading -> Unit // brief; avoids a flashing empty state
                state.isEmpty -> EmptyState(isSearchMiss = state.isSearchMiss)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 4.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        items = state.items,
                        key = { it.id },
                    ) { item ->
                        ClipboardRow(
                            item = item,
                            onPaste = onPaste,
                            onTogglePin = onTogglePin,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    val colors = BornomalaTheme.keyboardColors
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = "Search clipboard history" },
        singleLine = true,
        placeholder = { Text("Search clipboard") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.semantics {
                        contentDescription = "Clear search"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                    )
                }
            }
        } else {
            null
        },
        shape = RoundedCornerShape(BornomalaTheme.shapes.keyCornerRadius),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.keyBackground,
            unfocusedContainerColor = colors.keyBackground,
            focusedTextColor = colors.keyContent,
            unfocusedTextColor = colors.keyContent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ClipboardRow(
    item: ClipboardItem,
    onPaste: (String) -> Unit,
    onTogglePin: (id: Long, currentlyPinned: Boolean) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    val colors = BornomalaTheme.keyboardColors
    val pasteLabel = "Paste clipboard item: ${item.text}"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.keyBackground,
        contentColor = colors.keyContent,
        shape = RoundedCornerShape(BornomalaTheme.shapes.keyCornerRadius),
        onClick = { onPaste(item.text) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .heightIn(min = BornomalaTheme.dimens.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.text,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = pasteLabel },
                color = colors.keyContent,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            RowAction(
                onClick = { onTogglePin(item.id, item.pinned) },
                icon = if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (item.pinned) {
                    "Unpin clipboard item"
                } else {
                    "Pin clipboard item"
                },
                tint = if (item.pinned) colors.suggestionTextHighlighted else colors.functionalKeyContent,
            )
            RowAction(
                onClick = { onDelete(item.id) },
                icon = Icons.Filled.Delete,
                contentDescription = "Delete clipboard item",
                tint = colors.functionalKeyContent,
            )
        }
    }
}

@Composable
private fun RowAction(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(BornomalaTheme.dimens.minTouchTarget)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
        )
    }
}

@Composable
private fun EmptyState(isSearchMiss: Boolean) {
    val colors = BornomalaTheme.keyboardColors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentPaste,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Transparent),
                tint = colors.functionalKeyContent,
            )
            Text(
                text = if (isSearchMiss) {
                    "No matching clips"
                } else {
                    "Copied text will appear here"
                },
                color = LocalContentColor.current,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClipboardPanelPreview() {
    BornomalaTheme {
        ClipboardPanelContent(
            state = ClipboardUiState(
                items = listOf(
                    ClipboardItem(1, "https://bornomala.example/keyboard", pinned = true, createdAt = 3),
                    ClipboardItem(2, "আমি বাংলায় গান গাই", pinned = false, createdAt = 2),
                    ClipboardItem(3, "one-time passcode: 482913", pinned = false, createdAt = 1),
                ),
                query = "",
                isLoading = false,
            ),
            onQueryChange = {},
            onClearQuery = {},
            onPaste = {},
            onTogglePin = { _, _ -> },
            onDelete = {},
        )
    }
}
