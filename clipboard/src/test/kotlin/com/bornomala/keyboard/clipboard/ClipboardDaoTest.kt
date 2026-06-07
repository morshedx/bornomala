package com.bornomala.keyboard.clipboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bornomala.keyboard.clipboard.data.local.ClipboardDao
import com.bornomala.keyboard.clipboard.data.local.ClipboardDatabase
import com.bornomala.keyboard.clipboard.data.local.ClipboardEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO-level tests for the 100-item eviction cap, pin protection, and search, exercised
 * against a real in-memory Room database via Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ClipboardDaoTest {

    private lateinit var db: ClipboardDatabase
    private lateinit var dao: ClipboardDao

    private val cap = 100

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ClipboardDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.clipboardDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun add(text: String, pinned: Boolean = false, createdAt: Long) =
        dao.addAndEvict(
            ClipboardEntity(text = text, pinned = pinned, createdAt = createdAt),
            maxUnpinned = cap,
        )

    @Test
    fun `caps non-pinned items at 100 evicting the oldest`() = runTest {
        for (i in 1..120) {
            add(text = "item-$i", createdAt = i.toLong())
        }

        val all = dao.observeAll().first()
        assertThat(all).hasSize(cap)

        // Oldest 20 (item-1..item-20) should be gone; newest 100 retained.
        val texts = all.map { it.text }.toSet()
        assertThat(texts).doesNotContain("item-1")
        assertThat(texts).doesNotContain("item-20")
        assertThat(texts).contains("item-21")
        assertThat(texts).contains("item-120")
    }

    @Test
    fun `pinned items are protected from eviction and do not count against cap`() = runTest {
        // Pin 5 oldest items.
        for (i in 1..5) {
            add(text = "pinned-$i", pinned = true, createdAt = i.toLong())
        }
        // Push 130 unpinned items through.
        for (i in 1..130) {
            add(text = "free-$i", createdAt = (1000 + i).toLong())
        }

        val all = dao.observeAll().first()
        val texts = all.map { it.text }.toSet()

        // All pins survive.
        for (i in 1..5) {
            assertThat(texts).contains("pinned-$i")
        }
        // Unpinned trimmed to exactly the cap.
        val unpinnedCount = all.count { !it.pinned }
        assertThat(unpinnedCount).isEqualTo(cap)
        // Total = pins + capped unpinned.
        assertThat(all).hasSize(cap + 5)
    }

    @Test
    fun `pinned first then newest first ordering`() = runTest {
        add(text = "old-free", createdAt = 1)
        add(text = "new-free", createdAt = 3)
        add(text = "pinned", pinned = true, createdAt = 2)

        val all = dao.observeAll().first()
        assertThat(all.map { it.text }).containsExactly("pinned", "new-free", "old-free").inOrder()
    }

    @Test
    fun `search matches case-insensitive substring`() = runTest {
        add(text = "Hello World", createdAt = 1)
        add(text = "goodbye", createdAt = 2)
        add(text = "WORLDLY wisdom", createdAt = 3)

        val results = dao.search("%world%").first()
        assertThat(results.map { it.text })
            .containsExactly("WORLDLY wisdom", "Hello World").inOrder()
    }

    @Test
    fun `duplicate text refreshes existing row instead of inserting`() = runTest {
        val firstId = add(text = "same", createdAt = 1)
        add(text = "other", createdAt = 2)
        val secondId = add(text = "same", createdAt = 3)

        assertThat(secondId).isEqualTo(firstId)
        val all = dao.observeAll().first()
        assertThat(all).hasSize(2)
        // Refreshed item floats to top by createdAt.
        assertThat(all.first().text).isEqualTo("same")
    }

    @Test
    fun `evictOverflow keeps pinned items even when over cap`() = runTest {
        for (i in 1..110) {
            add(text = "p-$i", pinned = true, createdAt = i.toLong())
        }
        val all = dao.observeAll().first()
        // 110 pinned items, none evicted (cap governs unpinned only).
        assertThat(all).hasSize(110)
    }
}
