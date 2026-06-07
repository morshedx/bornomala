package com.bornomala.keyboard.ime.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bornomala.keyboard.clipboard.domain.repository.ClipboardRepository
import com.bornomala.keyboard.clipboard.presentation.ClipboardPanelContent
import com.bornomala.keyboard.clipboard.presentation.ClipboardViewModel
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hosts the clipboard module's panel inside the IME. The IME composition has no Hilt
 * `ViewModelStoreOwner`, so [androidx.hilt.navigation.compose.hiltViewModel] cannot be used;
 * instead the [ClipboardViewModel]'s singleton dependencies are pulled via a Hilt
 * [EntryPoint] and the view model is built directly, then driven through the stateless
 * [ClipboardPanelContent]. The view model is `remember`ed so it survives recomposition.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ClipboardHostEntryPoint {
    fun clipboardRepository(): ClipboardRepository
    fun dispatchers(): DispatcherProvider
}

@Composable
internal fun ClipboardHost(
    onPaste: (String) -> Unit,
    query: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel = remember {
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ClipboardHostEntryPoint::class.java,
        )
        ClipboardViewModel(deps.clipboardRepository(), deps.dispatchers())
    }
    androidx.compose.runtime.LaunchedEffect(query) { viewModel.onQueryChange(query) }
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
