package com.bornomala.keyboard.clipboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bornomala.keyboard.clipboard.data.local.ClipboardDatabase
import com.bornomala.keyboard.clipboard.data.repository.DefaultClipboardRepository
import com.bornomala.keyboard.clipboard.domain.repository.ClipboardRepository
import com.bornomala.keyboard.core.result.AppError
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.core.result.getOrNull
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for [DefaultClipboardRepository] over a real in-memory Room db,
 * covering validation, eviction, pin protection, unpin re-eviction, and search.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class DefaultClipboardRepositoryTest {

    private lateinit var db: ClipboardDatabase
    private lateinit var repository: ClipboardRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ClipboardDatabase::class.java,
        ).allowMainThreadQueries().build()
        // Use the unconfined-style immediate dispatcher via StandardTestDispatcher driven
        // by runTest's scheduler.
        repository = DefaultClipboardRepository(
            dao = db.clipboardDao(),
            dispatchers = TestDispatcherProvider(Dispatchers.Unconfined),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `addItem rejects blank text`() = runTest {
        val result = repository.addItem("   ")
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        val failure = result as AppResult.Failure
        assertThat(failure.error).isInstanceOf(AppError.Validation::class.java)
    }

    @Test
    fun `addItem trims and stores`() = runTest {
        repository.addItem("  hello  ")
        val items = repository.observeHistory().first()
        assertThat(items).hasSize(1)
        assertThat(items.first().text).isEqualTo("hello")
    }

    @Test
    fun `enforces 100 item cap on non-pinned`() = runTest {
        for (i in 1..(ClipboardRepository.MAX_HISTORY_ITEMS + 50)) {
            repository.addItem("item-$i")
        }
        val items = repository.observeHistory().first()
        assertThat(items).hasSize(ClipboardRepository.MAX_HISTORY_ITEMS)
    }

    @Test
    fun `pinned item survives eviction`() = runTest {
        val id = repository.addItem("keep-me").getOrNull()!!
        repository.setPinned(id, true)
        for (i in 1..(ClipboardRepository.MAX_HISTORY_ITEMS + 50)) {
            repository.addItem("noise-$i")
        }
        val items = repository.observeHistory().first()
        assertThat(items.any { it.text == "keep-me" && it.pinned }).isTrue()
    }

    @Test
    fun `unpinning re-enforces cap`() = runTest {
        val id = repository.addItem("formerly-pinned").getOrNull()!!
        repository.setPinned(id, true)
        for (i in 1..(ClipboardRepository.MAX_HISTORY_ITEMS + 50)) {
            repository.addItem("noise-$i")
        }
        // Now there are 100 unpinned + 1 pinned = 101.
        repository.setPinned(id, false)
        val items = repository.observeHistory().first()
        // Unpinning pushes unpinned count to 101, eviction trims back to 100.
        assertThat(items).hasSize(ClipboardRepository.MAX_HISTORY_ITEMS)
    }

    @Test
    fun `setPinned on missing id returns NotFound`() = runTest {
        val result = repository.setPinned(9999, true)
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error)
            .isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `deleteItem removes the row`() = runTest {
        val id = repository.addItem("delete-me").getOrNull()!!
        repository.deleteItem(id)
        val items = repository.observeHistory().first()
        assertThat(items).isEmpty()
    }

    @Test
    fun `clearUnpinned retains pinned`() = runTest {
        val pinnedId = repository.addItem("pinned").getOrNull()!!
        repository.setPinned(pinnedId, true)
        repository.addItem("free-1")
        repository.addItem("free-2")

        repository.clearUnpinned()

        val items = repository.observeHistory().first()
        assertThat(items.map { it.text }).containsExactly("pinned")
    }

    @Test
    fun `search returns case-insensitive substring matches`() = runTest {
        repository.addItem("Bornomala Keyboard")
        repository.addItem("random note")
        repository.addItem("keyBOARD shortcut")

        val results = repository.searchHistory("keyboard").first()
        assertThat(results.map { it.text })
            .containsExactly("keyBOARD shortcut", "Bornomala Keyboard")
    }

    @Test
    fun `search treats wildcards as literals`() = runTest {
        repository.addItem("100% sure")
        repository.addItem("0 percent")

        val results = repository.searchHistory("100%").first()
        assertThat(results.map { it.text }).containsExactly("100% sure")
    }

    @Test
    fun `blank search returns full history`() = runTest {
        repository.addItem("a")
        repository.addItem("b")
        val results = repository.searchHistory("   ").first()
        assertThat(results).hasSize(2)
    }
}
