package com.bornomala.keyboard.ime.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bornomala.keyboard.emoji.domain.repository.EmojiRepository
import com.bornomala.keyboard.emoji.presentation.EmojiPanel
import com.bornomala.keyboard.emoji.presentation.EmojiPanelViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hosts the emoji module's picker inside the IME, mirroring [ClipboardHost]: the
 * [EmojiPanelViewModel]'s singleton dependency is pulled via a Hilt [EntryPoint] and the view
 * model is built directly (the IME composition has no Hilt `ViewModelStoreOwner`), then driven
 * through the stateless [EmojiPanel]. Selecting an emoji commits its glyph and records usage;
 * the panel stays open for picking several in a row.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface EmojiHostEntryPoint {
    fun emojiRepository(): EmojiRepository
}

@Composable
internal fun EmojiHost(
    onEmoji: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel = remember {
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            EmojiHostEntryPoint::class.java,
        )
        EmojiPanelViewModel(deps.emojiRepository())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    EmojiPanel(
        state = state,
        onCategorySelected = viewModel::onCategorySelected,
        onQueryChanged = viewModel::onQueryChanged,
        onClearQuery = viewModel::onClearQuery,
        onEmojiSelected = { emoji ->
            viewModel.onEmojiUsed(emoji)
            onEmoji(emoji.glyph)
        },
        modifier = modifier,
    )
}
