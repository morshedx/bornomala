package com.bornomala.keyboard.clipboard

import app.cash.turbine.test
import com.bornomala.keyboard.clipboard.domain.model.ClipboardItem
import com.bornomala.keyboard.clipboard.domain.repository.ClipboardRepository
import com.bornomala.keyboard.clipboard.presentation.ClipboardViewModel
import com.bornomala.keyboard.core.result.AppResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ClipboardViewModel] using a fully in-memory fake repository so the
 * search debounce, pin toggle, and delete wiring are verified without Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeClipboardRepository
    private lateinit var viewModel: ClipboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeClipboardRepository()
        viewModel = ClipboardViewModel(repository, TestDispatcherProvider(testDispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits full history initially`() = runTest(testDispatcher) {
        repository.seed(
            ClipboardItem(1, "alpha", pinned = true, createdAt = 2),
            ClipboardItem(2, "beta", pinned = false, createdAt = 1),
        )
        viewModel.uiState.test {
            // initial loading state
            assertThat(awaitItem().isLoading).isTrue()
            val loaded = awaitItem()
            assertThat(loaded.isLoading).isFalse()
            assertThat(loaded.items.map { it.text }).containsExactly("alpha", "beta").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search query filters items`() = runTest(testDispatcher) {
        repository.seed(
            ClipboardItem(1, "hello world", pinned = false, createdAt = 2),
            ClipboardItem(2, "goodbye", pinned = false, createdAt = 1),
        )
        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // full list
            viewModel.onQueryChange("hello")
            val filtered = awaitItem()
            assertThat(filtered.query).isEqualTo("hello")
            assertThat(filtered.items.map { it.text }).containsExactly("hello world")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle pin delegates to repository`() = runTest(testDispatcher) {
        repository.seed(ClipboardItem(7, "x", pinned = false, createdAt = 1))
        viewModel.onTogglePin(7, currentlyPinned = false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(repository.pinCalls).containsExactly(7L to true)
    }

    @Test
    fun `delete delegates to repository`() = runTest(testDispatcher) {
        repository.seed(ClipboardItem(9, "x", pinned = false, createdAt = 1))
        viewModel.onDelete(9)
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(repository.deletedIds).containsExactly(9L)
    }

    @Test
    fun `clear unpinned delegates to repository`() = runTest(testDispatcher) {
        viewModel.onClearUnpinned()
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(repository.clearUnpinnedCalls).isEqualTo(1)
    }

    /** In-memory fake honoring ordering, search, pin, and delete semantics. */
    private class FakeClipboardRepository : ClipboardRepository {
        private val state = MutableStateFlow<List<ClipboardItem>>(emptyList())
        val pinCalls = mutableListOf<Pair<Long, Boolean>>()
        val deletedIds = mutableListOf<Long>()
        var clearUnpinnedCalls = 0

        fun seed(vararg items: ClipboardItem) {
            state.value = items.sortedWith(
                compareByDescending<ClipboardItem> { it.pinned }.thenByDescending { it.createdAt },
            )
        }

        override fun observeHistory(): Flow<List<ClipboardItem>> = state

        override fun searchHistory(query: String): Flow<List<ClipboardItem>> {
            val q = query.trim()
            return if (q.isEmpty()) {
                state
            } else {
                state.map { list -> list.filter { it.text.contains(q, ignoreCase = true) } }
            }
        }

        override suspend fun addItem(text: String): AppResult<Long> {
            val id = (state.value.maxOfOrNull { it.id } ?: 0L) + 1
            state.update { it + ClipboardItem(id, text, false, id) }
            return AppResult.success(id)
        }

        override suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Unit> {
            pinCalls += id to pinned
            state.update { list -> list.map { if (it.id == id) it.copy(pinned = pinned) else it } }
            return AppResult.success(Unit)
        }

        override suspend fun deleteItem(id: Long): AppResult<Unit> {
            deletedIds += id
            state.update { list -> list.filterNot { it.id == id } }
            return AppResult.success(Unit)
        }

        override suspend fun clearUnpinned(): AppResult<Unit> {
            clearUnpinnedCalls++
            state.update { list -> list.filter { it.pinned } }
            return AppResult.success(Unit)
        }

        override suspend fun exportAll(): AppResult<List<ClipboardItem>> =
            AppResult.success(state.value)

        override suspend fun replaceAll(items: List<ClipboardItem>): AppResult<Unit> {
            state.value = items
            return AppResult.success(Unit)
        }
    }
}
