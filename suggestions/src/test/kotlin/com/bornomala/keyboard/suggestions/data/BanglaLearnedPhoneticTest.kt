package com.bornomala.keyboard.suggestions.data

import com.bornomala.keyboard.suggestions.data.dictionary.BanglaPhoneticKey
import com.bornomala.keyboard.suggestions.data.dictionary.BanglaPhoneticRepository
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryEntity
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.util.FakeUserDictionaryDao
import com.bornomala.keyboard.suggestions.util.InMemoryDictionarySource
import com.bornomala.keyboard.suggestions.util.TestDispatcherProvider
import com.bornomala.keyboard.suggestions.util.lazyOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * "Learn from my typing" for Bangla: words the user commits are keyed phonetically, so roman
 * input resolves to them alongside the bundled index — and, once trusted, ahead of it.
 */
class BanglaLearnedPhoneticTest {

    private val dispatchers = TestDispatcherProvider()
    private val dao = FakeUserDictionaryDao()
    private val userDictionary = UserDictionaryRepository(lazyOf(dao), dispatchers)

    /** A bundled index where `sosa` already resolves to a word the user did not type. */
    private val phonetic = BanglaPhoneticRepository(
        InMemoryDictionarySource(
            data = emptyMap(),
            phonetic = mapOf(SuggestionLanguage.BANGLA to listOf("ssa\tসসা")),
        ),
        dispatchers,
    )

    private fun engine() = DefaultSuggestionEngine(
        providers = emptySet(),
        dispatchers = dispatchers,
        banglaPhonetic = phonetic,
        userDictionary = userDictionary,
    )

    private suspend fun learn(word: String, times: Int) {
        repeat(times) { userDictionary.learn(word, previousWord = "", language = SuggestionLanguage.BANGLA) }
    }

    @Test
    fun `learned word is keyed phonetically on commit`() = runTest {
        learn("শশা", times = 1)
        val stored = dao.findExact("শশা", SuggestionLanguage.BANGLA.code)
        assertThat(stored?.phoneticKey).isEqualTo(BanglaPhoneticKey.romanKey("shosha"))
    }

    @Test
    fun `a word typed once trails the bundled index`() = runTest {
        learn("শশা", times = 1)
        val result = engine().banglaPhoneticCandidates("shosha", 5)
        assertThat(result).containsExactly("সসা", "শশা").inOrder()
    }

    @Test
    fun `a word typed repeatedly leads the bundled index`() = runTest {
        learn("শশা", times = 2)
        val result = engine().banglaPhoneticCandidates("shosha", 5)
        assertThat(result).containsExactly("শশা", "সসা").inOrder()
    }

    @Test
    fun `learned words only answer their own key`() = runTest {
        learn("শশা", times = 3)
        assertThat(engine().banglaPhoneticCandidates("sa", 5)).doesNotContain("শশা")
    }

    @Test
    fun `english learning stores no phonetic key`() = runTest {
        userDictionary.learn("hello", previousWord = "", language = SuggestionLanguage.ENGLISH)
        assertThat(dao.findExact("hello", SuggestionLanguage.ENGLISH.code)?.phoneticKey).isEmpty()
    }

    @Test
    fun `rows learned before the key column are backfilled on first lookup`() = runTest {
        // A pre-migration row: learned, but with no phonetic key yet.
        dao.insertWords(
            listOf(UserDictionaryEntity("শশা", SuggestionLanguage.BANGLA.code, 5, 1L, "", "")),
        )
        val result = engine().banglaPhoneticCandidates("shosha", 5)
        assertThat(result).contains("শশা")
        assertThat(dao.findExact("শশা", SuggestionLanguage.BANGLA.code)?.phoneticKey).isEqualTo("ssa")
    }
}
