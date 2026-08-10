package com.bornomala.keyboard.suggestions.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Verifies the real Room DAO SQL: atomic upsert-increment and ranked queries. */
@RunWith(RobolectricTestRunner::class)
class UserDictionaryDaoRoomTest {

    private lateinit var db: SuggestionsDatabase
    private lateinit var dao: UserDictionaryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SuggestionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.userDictionaryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `learnWord inserts then increments frequency atomically`() = runTest {
        dao.learnWord("hello", "en", "", 1L, "")
        dao.learnWord("hello", "en", "world", 2L, "")
        dao.learnWord("hello", "en", "again", 3L, "")

        val entry = dao.findExact("hello", "en")
        assertThat(entry).isNotNull()
        assertThat(entry!!.frequency).isEqualTo(3)
        assertThat(entry.lastUsed).isEqualTo(3L)
        assertThat(entry.prevWord).isEqualTo("again")
    }

    @Test
    fun `same word in different languages are independent`() = runTest {
        dao.learnWord("ma", "en", "", 1L, "")
        dao.learnWord("ma", "bn", "", 1L, "")
        assertThat(dao.count("en")).isEqualTo(1)
        assertThat(dao.count("bn")).isEqualTo(1)
    }

    @Test
    fun `queryByPrefix orders by frequency then recency`() = runTest {
        dao.learnWord("the", "en", "", 1L, "")
        dao.learnWord("the", "en", "", 2L, "") // freq 2
        dao.learnWord("their", "en", "", 3L, "") // freq 1, newer
        dao.learnWord("them", "en", "", 1L, "") // freq 1, older

        val result = dao.queryByPrefix("en", "the", 10)
        assertThat(result.map { it.word }).containsExactly("the", "their", "them").inOrder()
    }

    @Test
    fun `queryNextWord returns words learned after the previous word`() = runTest {
        dao.learnWord("morning", "en", "good", 1L, "")
        dao.learnWord("morning", "en", "good", 2L, "")
        dao.learnWord("night", "en", "good", 3L, "")
        dao.learnWord("idea", "en", "great", 4L, "")

        val result = dao.queryNextWord("en", "good", 10)
        assertThat(result.map { it.word }).containsExactly("morning", "night").inOrder()
    }

    @Test
    fun `delete removes a single entry`() = runTest {
        dao.learnWord("hello", "en", "", 1L, "")
        dao.delete("hello", "en")
        assertThat(dao.findExact("hello", "en")).isNull()
    }

    @Test
    fun `clear empties the table`() = runTest {
        dao.learnWord("a", "en", "", 1L, "")
        dao.learnWord("b", "bn", "", 1L, "")
        dao.clear()
        assertThat(dao.count("en")).isEqualTo(0)
        assertThat(dao.count("bn")).isEqualTo(0)
    }
}
