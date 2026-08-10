package com.bornomala.keyboard.clipboard.presentation

import com.bornomala.keyboard.theme.LucideIcons

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    // The clip whose action menu (Pin/Paste/Delete) is open, or null. A tap pastes immediately;
    // long-press opens this menu instead of cluttering every card with inline icons.
    var menuItem by remember { mutableStateOf<ClipboardItem?>(null) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp),
        color = colors.keyboardBackground,
        contentColor = colors.keyContent,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // No in-panel search field: an IME cannot type into its own UI without a
                // sub-keyboard. `onQueryChange`/`onClearQuery` are unused here.
                when {
                    state.isLoading -> Unit // brief; avoids a flashing empty state
                    state.isEmpty -> EmptyState(isSearchMiss = state.isSearchMiss)
                    // Two-column masonry: clips vary in length, so a staggered grid packs short and
                    // long cards without the ragged gaps a fixed grid would leave.
                    else -> LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 4.dp,
                        ),
                        verticalItemSpacing = 6.dp,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = state.items,
                            key = { it.id },
                        ) { item ->
                            ClipboardCard(
                                item = item,
                                onPaste = onPaste,
                                onLongPress = { menuItem = item },
                            )
                        }
                    }
                }
            }

            // Long-press action menu, drawn over the panel with a dim scrim (Gboard-style).
            val active = menuItem
            if (active != null) {
                ClipActionMenu(
                    item = active,
                    onPaste = {
                        onPaste(active.text)
                        menuItem = null
                    },
                    onTogglePin = {
                        onTogglePin(active.id, active.pinned)
                        menuItem = null
                    },
                    onDelete = {
                        onDelete(active.id)
                        menuItem = null
                    },
                    onDismiss = { menuItem = null },
                    modifier = Modifier.matchParentSize(),
                )
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
                imageVector = LucideIcons.Search,
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
                        imageVector = LucideIcons.Trash,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardCard(
    item: ClipboardItem,
    onPaste: (String) -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = BornomalaTheme.keyboardColors
    val pasteLabel = "Paste clipboard item: ${item.text}"
    val shape = RoundedCornerShape(BornomalaTheme.shapes.keyCornerRadius)
    // Just the clip text now — a tap pastes, a long-press opens the action menu. The text takes
    // the full card width and can run several lines, which is what gives the masonry its varied
    // card heights.
    Surface(
        color = colors.keyBackground,
        contentColor = colors.keyContent,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = { onPaste(item.text) },
                onLongClick = onLongPress,
                onClickLabel = "Paste",
                onLongClickLabel = "More actions",
            ),
    ) {
        Text(
            text = item.text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clearAndSetSemantics { contentDescription = pasteLabel },
            color = colors.keyContent,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Long-press action sheet for a clip: a dimmed scrim over the panel with the clip preview and a
 * Pin / Paste / Delete button row. Tapping the scrim (anywhere outside a button) dismisses it.
 */
@Composable
private fun ClipActionMenu(
    item: ClipboardItem,
    onPaste: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BornomalaTheme.keyboardColors
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = colors.popupBackground,
                contentColor = colors.popupContent,
                shape = RoundedCornerShape(BornomalaTheme.shapes.keyCornerRadius),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    color = colors.popupContent,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MenuButton(
                    modifier = Modifier.weight(1f),
                    icon = LucideIcons.Pin,
                    label = if (item.pinned) "Unpin" else "Pin",
                    onClick = onTogglePin,
                )
                MenuButton(
                    modifier = Modifier.weight(1f),
                    icon = LucideIcons.Clipboard,
                    label = "Paste",
                    onClick = onPaste,
                )
                MenuButton(
                    modifier = Modifier.weight(1f),
                    icon = LucideIcons.Trash,
                    label = "Delete",
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun MenuButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = BornomalaTheme.keyboardColors
    Surface(
        modifier = modifier.semantics { contentDescription = label },
        color = colors.keyBackground,
        contentColor = colors.keyContent,
        shape = RoundedCornerShape(BornomalaTheme.shapes.keyCornerRadius),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.functionalKeyContent)
            Spacer(Modifier.width(8.dp))
            Text(text = label, color = colors.keyContent, style = MaterialTheme.typography.bodyMedium)
        }
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
                imageVector = LucideIcons.Clipboard,
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
