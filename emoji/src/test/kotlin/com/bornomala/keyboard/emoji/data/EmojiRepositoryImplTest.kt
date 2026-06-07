package com.bornomala.keyboard.emoji.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.bornomala.keyboard.emoji.data.catalog.EmojiCatalog
import com.bornomala.keyboard.emoji.data.local.EmojiDatabase
import com.bornomala.keyboard.emoji.data.local.EmojiUsageDao
import com.bornomala.keyboard.emoji.domain.model.EmojiCategory
import com.bornomala.keyboard.emoji.util.TestDispatcherProvider
import com.bornomala.keyboard.core.result.getOrDefault
import com.bornomala.keyboard.core.result.AppResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class EmojiRepositoryImplTest {

    private lateinit var db: EmojiDatabase
    private lateinit var dao: EmojiUsageDao
    private lateinit var repository: EmojiRepositoryImpl

    // Sample real catalog emoji so glyph resolution in the repo succeeds.
    private val grinning = EmojiCatalog.all.first { it.glyph == "😀" }
    private val pizza = EmojiCatalog.all.first { it.glyph == "🍕" }
    private val heart = EmojiCatalog.all.first { it.glyph == "❤️" }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EmojiDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.emojiUsageDao()
        // Real DAO + IO operations; route dispatchers to the unconfined-ish test path.
        repository = EmojiRepositoryImpl(dao, TestDispatcherProvider(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `emojisFor returns catalog entries for static category`() = runTest {
        val result = repository.emojisFor(EmojiCategory.FOOD)
        val list = result.getOrDefault(emptyList())
        assertThat(list).isNotEmpty()
        assertThat(list.all { it.category == EmojiCategory.FOOD }).isTrue()
    }

    @Test
    fun `emojisFor returns empty for dynamic RECENT`() = runTest {
        val list = repository.emojisFor(EmojiCategory.RECENT).getOrDefault(emptyList())
        assertThat(list).isEmpty()
    }

    @Test
    fun `search delegates to index`() = runTest {
        val list = repository.search("pizza").getOrDefault(emptyList())
        assertThat(list.map { it.glyph }).contains("🍕")
    }

    @Test
    fun `recordUsage persists and increments count`() = runTest {
        assertThat(repository.recordUsage(pizza)).isInstanceOf(AppResult.Success::class.java)
        repository.recordUsage(pizza)
        repository.recordUsage(pizza)

        repository.observeFrequent(10).test {
            val emitted = awaitItem()
            assertThat(emitted.first().glyph).isEqualTo("🍕")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeRecent orders most recently used first`() = runTest {
        repository.recordUsage(grinning)
        repository.recordUsage(pizza)
        repository.recordUsage(heart)

        repository.observeRecent(10).test {
            val emitted = awaitItem()
            // heart was recorded last => most recent first.
            assertThat(emitted.first().glyph).isEqualTo("❤️")
            assertThat(emitted.map { it.glyph }).containsExactly("❤️", "🍕", "😀").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeFrequent orders by usage count`() = runTest {
        repeat(3) { repository.recordUsage(grinning) }
        repeat(5) { repository.recordUsage(pizza) }
        repeat(1) { repository.recordUsage(heart) }

        repository.observeFrequent(10).test {
            val emitted = awaitItem()
            assertThat(emitted.map { it.glyph }).containsExactly("🍕", "😀", "❤️").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeRecent respects limit`() = runTest {
        repository.recordUsage(grinning)
        repository.recordUsage(pizza)
        repository.recordUsage(heart)

        repository.observeRecent(2).test {
            val emitted = awaitItem()
            assertThat(emitted).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-recording an emoji moves it to most recent`() = runTest {
        repository.recordUsage(grinning)
        repository.recordUsage(pizza)
        // grinning used again => should now be most recent.
        repository.recordUsage(grinning)

        repository.observeRecent(10).test {
            val emitted = awaitItem()
            assertThat(emitted.first().glyph).isEqualTo("😀")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearUsage removes all history`() = runTest {
        repository.recordUsage(pizza)
        repository.recordUsage(heart)
        assertThat(repository.clearUsage()).isInstanceOf(AppResult.Success::class.java)

        repository.observeRecent(10).test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unknown glyph in history is filtered out of results`() = runTest {
        // Insert a glyph not present in the catalog directly via DAO.
        dao.upsertUsage("☃️-not-in-catalog", System.currentTimeMillis())
        repository.recordUsage(pizza)

        repository.observeRecent(10).test {
            val emitted = awaitItem()
            // Only the catalog-resolvable pizza survives mapping.
            assertThat(emitted.map { it.glyph }).containsExactly("🍕")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
