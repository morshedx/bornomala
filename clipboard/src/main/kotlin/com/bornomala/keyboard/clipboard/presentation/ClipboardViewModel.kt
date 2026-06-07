package com.bornomala.keyboard.clipboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bornomala.keyboard.clipboard.domain.repository.ClipboardRepository
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the [ClipboardPanel]. Holds the search query and exposes a single
 * [ClipboardUiState] flow.
 *
 * The search query feeds a debounced [flatMapLatest] so each keystroke does not spawn a
 * fresh DB query; queries coalesce on a short idle window. All mutations are fire-and-
 * forget on [viewModelScope]; the repository persists off the main thread and the
 * resulting change re-emits through the observed flow, so the UI updates reactively.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    val uiState: StateFlow<ClipboardUiState> = queryFlow
        .debounce { query -> if (query.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            repository.searchHistory(query).map { items ->
                ClipboardUiState(
                    items = items,
                    query = query,
                    isLoading = false,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ClipboardUiState(),
        )

    fun onQueryChange(query: String) {
        queryFlow.update { query }
    }

    fun onClearQuery() {
        queryFlow.update { "" }
    }

    fun onTogglePin(id: Long, currentlyPinned: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            repository.setPinned(id, !currentlyPinned)
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch(dispatchers.io) {
            repository.deleteItem(id)
        }
    }

    fun onClearUnpinned() {
        viewModelScope.launch(dispatchers.io) {
            repository.clearUnpinned()
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 180L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
