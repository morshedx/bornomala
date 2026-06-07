package com.bornomala.keyboard.emoji.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bornomala.keyboard.core.result.getOrDefault
import com.bornomala.keyboard.emoji.domain.model.Emoji
import com.bornomala.keyboard.emoji.domain.model.EmojiCategory
import com.bornomala.keyboard.emoji.domain.repository.EmojiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the [EmojiPanel]. Holds the selected category and search query, resolves the
 * appropriate emoji to display, and records usage when the user picks an emoji.
 *
 * All heavy work is delegated to [EmojiRepository] (catalog/search on Default, usage on
 * IO); the ViewModel only orchestrates flows and never blocks the Main thread. Recent /
 * frequent rows are cold Room flows shared while the panel is on screen.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class EmojiPanelViewModel @Inject constructor(
    private val repository: EmojiRepository,
) : ViewModel() {

    private val selectedCategory = MutableStateFlow(EmojiCategory.RECENT)
    private val query = MutableStateFlow("")

    private val categoryEmojis: StateFlow<List<Emoji>> =
        selectedCategory
            .flatMapLatest { category ->
                flowOf(repository.emojisFor(category).getOrDefault(emptyList()))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val searchResults: StateFlow<List<Emoji>> =
        query
            .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isBlank()) {
                    flowOf(emptyList())
                } else {
                    flowOf(repository.search(q).getOrDefault(emptyList()))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val recent: StateFlow<List<Emoji>> =
        repository.observeRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val frequent: StateFlow<List<Emoji>> =
        repository.observeFrequent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val state: StateFlow<EmojiPanelState> = combine(
        selectedCategory,
        query,
        categoryEmojis,
        recent,
        frequent,
    ) { category, q, catEmojis, recentList, frequentList ->
        Quintuple(category, q, catEmojis, recentList, frequentList)
    }.combine(searchResults) { (category, q, catEmojis, recentList, frequentList), results ->
        EmojiPanelState(
            selectedCategory = category,
            query = q,
            categoryEmojis = catEmojis,
            recent = recentList,
            frequent = frequentList,
            searchResults = results,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        EmojiPanelState(),
    )

    init {
        // Keep recent/frequent warm while the panel is observed so the first frame of
        // the RECENT tab has data without a visible delay.
        recent.onEach { }.launchIn(viewModelScope)
        frequent.onEach { }.launchIn(viewModelScope)
    }

    fun onCategorySelected(category: EmojiCategory) {
        selectedCategory.value = category
        if (query.value.isNotEmpty()) query.value = ""
    }

    fun onQueryChanged(newQuery: String) {
        query.value = newQuery
    }

    fun onClearQuery() {
        query.value = ""
    }

    /** Persists that the user inserted [emoji]; fire-and-forget within the VM scope. */
    fun onEmojiUsed(emoji: Emoji) {
        viewModelScope.launch { repository.recordUsage(emoji) }
    }

    private data class Quintuple(
        val category: EmojiCategory,
        val query: String,
        val categoryEmojis: List<Emoji>,
        val recent: List<Emoji>,
        val frequent: List<Emoji>,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 120L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
