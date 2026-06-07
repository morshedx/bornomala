package com.bornomala.keyboard.emoji.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.bornomala.keyboard.emoji.domain.model.Emoji
import com.bornomala.keyboard.emoji.domain.model.EmojiCategory
import com.bornomala.keyboard.theme.BornomalaTheme

/**
 * Test tags for instrumented / UI tests of the emoji panel.
 */
object EmojiPanelTestTags {
    const val SEARCH_FIELD = "emoji_search_field"
    const val CLEAR_SEARCH = "emoji_clear_search"
    const val GRID = "emoji_grid"
    const val CATEGORY_TABS = "emoji_category_tabs"
    const val RECENT_ROW = "emoji_recent_row"
    const val FREQUENT_ROW = "emoji_frequent_row"
    const val EMPTY = "emoji_empty"
    fun tab(category: EmojiCategory) = "emoji_tab_${category.id}"
    fun emoji(glyph: String) = "emoji_cell_$glyph"
}

/**
 * Entry point composable wired to Hilt. Hosts collect [EmojiPanelViewModel] state and
 * forward emoji selections to the [InputConnection] via [onEmojiSelected].
 *
 * @param onEmojiSelected invoked with the chosen [Emoji] so the IME can commit its
 *   glyph; usage is recorded automatically by the ViewModel.
 */
@Composable
fun EmojiPanel(
    onEmojiSelected: (Emoji) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmojiPanelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EmojiPanel(
        state = state,
        onCategorySelected = viewModel::onCategorySelected,
        onQueryChanged = viewModel::onQueryChanged,
        onClearQuery = viewModel::onClearQuery,
        onEmojiSelected = { emoji ->
            viewModel.onEmojiUsed(emoji)
            onEmojiSelected(emoji)
        },
        modifier = modifier,
    )
}

/**
 * Stateless emoji panel. Separated from the Hilt entry point so it is fully previewable
 * and unit-testable with a fabricated [EmojiPanelState].
 */
@Composable
fun EmojiPanel(
    state: EmojiPanelState,
    onCategorySelected: (EmojiCategory) -> Unit,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onEmojiSelected: (Emoji) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BornomalaTheme.keyboardColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.keyboardBackground),
    ) {
        // No in-panel search field: an IME cannot type into its own UI without a sub-keyboard.
        // Navigation is via the category tabs below; `onQueryChanged`/`onClearQuery` are unused.
        if (!state.isSearching) {
            EmojiCategoryTabs(
                selected = state.selectedCategory,
                onCategorySelected = onCategorySelected,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isSearching -> EmojiResultsGrid(
                    emojis = state.searchResults,
                    emptyMessage = "No emoji found",
                    onEmojiSelected = onEmojiSelected,
                )

                state.selectedCategory.isDynamic -> RecentView(
                    recent = state.recent,
                    frequent = state.frequent,
                    onEmojiSelected = onEmojiSelected,
                )

                else -> EmojiResultsGrid(
                    emojis = state.categoryEmojis,
                    emptyMessage = "No emoji",
                    onEmojiSelected = onEmojiSelected,
                )
            }
        }
    }
}

@Composable
private fun EmojiSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    val colors = BornomalaTheme.keyboardColors
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(EmojiPanelTestTags.SEARCH_FIELD),
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear search",
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onClearQuery)
                        .testTag(EmojiPanelTestTags.CLEAR_SEARCH),
                )
            }
        },
        placeholder = { Text("Search emoji") },
        keyboardOptions = KeyboardOptions.Default,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.popupBackground,
            unfocusedContainerColor = colors.popupBackground,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun EmojiCategoryTabs(
    selected: EmojiCategory,
    onCategorySelected: (EmojiCategory) -> Unit,
) {
    val colors = BornomalaTheme.keyboardColors
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(BornomalaTheme.dimens.panelTabStripHeight)
            .background(colors.suggestionBarBackground)
            .testTag(EmojiPanelTestTags.CATEGORY_TABS),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(EmojiCategory.entries, key = { it.id }) { category ->
            val isSelected = category == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onCategorySelected(category) }
                    .testTag(EmojiPanelTestTags.tab(category))
                    .clearAndSetSemantics {
                        contentDescription = category.accessibilityLabel()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon(),
                    contentDescription = null,
                    tint = if (isSelected) {
                        colors.suggestionTextHighlighted
                    } else {
                        colors.functionalKeyContent
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentView(
    recent: List<Emoji>,
    frequent: List<Emoji>,
    onEmojiSelected: (Emoji) -> Unit,
) {
    if (recent.isEmpty() && frequent.isEmpty()) {
        EmptyState(message = "Emoji you use will appear here")
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (frequent.isNotEmpty()) {
            SectionHeader("Frequently used")
            EmojiHorizontalRow(
                emojis = frequent,
                rowTestTag = EmojiPanelTestTags.FREQUENT_ROW,
                onEmojiSelected = onEmojiSelected,
            )
        }
        if (recent.isNotEmpty()) {
            SectionHeader("Recent")
            EmojiHorizontalRow(
                emojis = recent,
                rowTestTag = EmojiPanelTestTags.RECENT_ROW,
                onEmojiSelected = onEmojiSelected,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = BornomalaTheme.keyboardColors.suggestionText,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmojiHorizontalRow(
    emojis: List<Emoji>,
    rowTestTag: String,
    onEmojiSelected: (Emoji) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(rowTestTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(emojis.distinctBy { it.glyph }, key = { it.glyph }) { emoji ->
            EmojiCell(emoji = emoji, onEmojiSelected = onEmojiSelected)
        }
    }
}

@Composable
private fun EmojiResultsGrid(
    emojis: List<Emoji>,
    emptyMessage: String,
    onEmojiSelected: (Emoji) -> Unit,
) {
    if (emojis.isEmpty()) {
        EmptyState(message = emptyMessage)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 44.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag(EmojiPanelTestTags.GRID),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items(emojis.distinctBy { it.glyph }, key = { it.glyph }) { emoji ->
            EmojiCell(emoji = emoji, onEmojiSelected = onEmojiSelected)
        }
    }
}

@Composable
private fun EmojiCell(
    emoji: Emoji,
    onEmojiSelected: (Emoji) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(BornomalaTheme.dimens.minTouchTarget)
            .clickable { onEmojiSelected(emoji) }
            .testTag(EmojiPanelTestTags.emoji(emoji.glyph))
            .clearAndSetSemantics { contentDescription = emoji.name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji.glyph,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(EmojiPanelTestTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = BornomalaTheme.keyboardColors.suggestionText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Icon shown on the category tab strip. */
private fun EmojiCategory.icon(): ImageVector = when (this) {
    EmojiCategory.RECENT -> Icons.Filled.AccessTime
    EmojiCategory.SMILEYS -> Icons.Filled.EmojiEmotions
    EmojiCategory.PEOPLE -> Icons.Filled.SentimentSatisfiedAlt
    EmojiCategory.ANIMALS -> Icons.Filled.Pets
    EmojiCategory.FOOD -> Icons.Filled.Fastfood
    EmojiCategory.ACTIVITY -> Icons.Filled.EmojiEvents
    EmojiCategory.TRAVEL -> Icons.Filled.DirectionsCar
    EmojiCategory.OBJECTS -> Icons.Filled.Lightbulb
    EmojiCategory.SYMBOLS -> Icons.Filled.EmojiSymbols
    EmojiCategory.FLAGS -> Icons.Filled.Flag
}

/** Spoken label for the category tab (TalkBack). */
private fun EmojiCategory.accessibilityLabel(): String = when (this) {
    EmojiCategory.RECENT -> "Recent emoji"
    EmojiCategory.SMILEYS -> "Smileys and emotion"
    EmojiCategory.PEOPLE -> "People and body"
    EmojiCategory.ANIMALS -> "Animals and nature"
    EmojiCategory.FOOD -> "Food and drink"
    EmojiCategory.ACTIVITY -> "Activities"
    EmojiCategory.TRAVEL -> "Travel and places"
    EmojiCategory.OBJECTS -> "Objects"
    EmojiCategory.SYMBOLS -> "Symbols"
    EmojiCategory.FLAGS -> "Flags"
}
