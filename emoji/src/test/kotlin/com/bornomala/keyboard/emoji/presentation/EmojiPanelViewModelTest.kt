package com.bornomala.keyboard.emoji.presentation

import app.cash.turbine.test
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.emoji.data.catalog.EmojiCatalog
import com.bornomala.keyboard.emoji.domain.model.Emoji
import com.bornomala.keyboard.emoji.domain.model.EmojiCategory
import com.bornomala.keyboard.emoji.domain.repository.EmojiRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class EmojiPanelViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository : EmojiRepository {
        val recorded = mutableListOf<Emoji>()
        val recentFlow = MutableStateFlow<List<Emoji>>(emptyList())
        val frequentFlow = MutableStateFlow<List<Emoji>>(emptyList())

        override suspend fun emojisFor(category: EmojiCategory): AppResult<List<Emoji>> =
            AppResult.Success(EmojiCatalog.byCategory[category].orEmpty())

        override suspend fun search(query: String): AppResult<List<Emoji>> =
            AppResult.Success(
                EmojiCatalog.all.filter { it.name.contains(query, ignoreCase = true) },
            )

        override fun observeRecent(limit: Int): Flow<List<Emoji>> = recentFlow

        override fun observeFrequent(limit: Int): Flow<List<Emoji>> = frequentFlow

        override suspend fun recordUsage(emoji: Emoji): AppResult<Unit> {
            recorded.add(emoji)
            return AppResult.Success(Unit)
        }

        override suspend fun clearUsage(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: EmojiPanelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        viewModel = EmojiPanelViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state selects recent category`() = runTest(dispatcher) {
        viewModel.state.test {
            val initial = awaitItem()
            assertThat(initial.selectedCategory).isEqualTo(EmojiCategory.RECENT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting a category loads its emoji`() = runTest(dispatcher) {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.onCategorySelected(EmojiCategory.FOOD)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.selectedCategory).isEqualTo(EmojiCategory.FOOD)
            assertThat(state.categoryEmojis).isNotEmpty()
            assertThat(state.categoryEmojis.all { it.category == EmojiCategory.FOOD }).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing a query produces search results`() = runTest(dispatcher) {
        viewModel.state.test {
            awaitItem()
            viewModel.onQueryChanged("pizza")
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.isSearching).isTrue()
            assertThat(state.searchResults.map { it.glyph }).contains("🍕")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing query exits search mode`() = runTest(dispatcher) {
        viewModel.state.test {
            awaitItem()
            viewModel.onQueryChanged("pizza")
            advanceUntilIdle()
            viewModel.onClearQuery()
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.isSearching).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting a category while searching clears the query`() = runTest(dispatcher) {
        viewModel.state.test {
            awaitItem()
            viewModel.onQueryChanged("dog")
            advanceUntilIdle()
            viewModel.onCategorySelected(EmojiCategory.ANIMALS)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.query).isEmpty()
            assertThat(state.selectedCategory).isEqualTo(EmojiCategory.ANIMALS)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `using an emoji records usage`() = runTest(dispatcher) {
        val pizza = EmojiCatalog.all.first { it.glyph == "🍕" }
        viewModel.onEmojiUsed(pizza)
        advanceUntilIdle()
        assertThat(repository.recorded).containsExactly(pizza)
    }

    @Test
    fun `recent flow surfaces in state`() = runTest(dispatcher) {
        val heart = EmojiCatalog.all.first { it.glyph == "❤️" }
        viewModel.state.test {
            awaitItem()
            repository.recentFlow.value = listOf(heart)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.recent.map { it.glyph }).contains("❤️")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
