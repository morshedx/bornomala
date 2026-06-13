package com.bornomala.keyboard.suggestions.data.provider

import com.bornomala.keyboard.suggestions.data.dictionary.BigramDictionaryRepository
import com.bornomala.keyboard.suggestions.data.dictionary.FrequencyDictionaryRepository
import com.bornomala.keyboard.suggestions.data.dictionary.OffensiveWordRepository
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository
import com.bornomala.keyboard.suggestions.domain.SuggestionProvider
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.domain.model.SuggestionRequest
import com.bornomala.keyboard.suggestions.domain.model.SuggestionSource
import com.bornomala.keyboard.suggestions.util.FakeUserDictionaryDao
import com.bornomala.keyboard.suggestions.util.InMemoryDictionarySource
import com.bornomala.keyboard.suggestions.util.TestDispatcherProvider
import com.bornomala.keyboard.suggestions.util.lazyOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/** Ranking, learning, and next-word behaviour of [OfflineProvider]. */
class OfflineProviderTest {

    private val dispatchers = TestDispatcherProvider()
    private lateinit var dao: FakeUserDictionaryDao
    private lateinit var userRepo: UserDictionaryRepository
    private lateinit var provider: OfflineProvider

    private val englishLines = listOf(
        "the\t100",
        "they\t80",
        "their\t90",
        "them\t70",
        "to\t60",
    )

    @Before
    fun setUp() {
        dao = FakeUserDictionaryDao()
        userRepo = UserDictionaryRepository(lazyOf(dao), dispatchers)
        val dictSource = InMemoryDictionarySource(mapOf(SuggestionLanguage.ENGLISH to englishLines))
        val dictRepo = FrequencyDictionaryRepository(dictSource, dispatchers)
        val bigramRepo = BigramDictionaryRepository(dictSource, dispatchers)
        provider = OfflineProvider(dictRepo, bigramRepo, userRepo, OffensiveWordRepository(dictSource, dispatchers))
    }

    private fun request(current: String, previous: String = "", limit: Int = 3) =
        SuggestionRequest(currentWord = current, previousWord = previous, language = SuggestionLanguage.ENGLISH, limit = limit)

    @Test
    fun `is always available offline`() {
        assertThat(provider.isAvailable(SuggestionLanguage.ENGLISH)).isTrue()
        assertThat(provider.priority).isEqualTo(SuggestionProvider.PRIORITY_OFFLINE)
    }

    @Test
    fun `ranks dictionary candidates by frequency`() = runTest {
        val result = (provider.suggest(request("the")) as com.bornomala.keyboard.core.result.AppResult.Success).data
        val words = result.map { it.word }
        // "the" (100) > "their" (90) > "they" (80)
        assertThat(words).containsAtLeast("the", "their", "they")
        assertThat(words.indexOf("the")).isLessThan(words.indexOf("their"))
        assertThat(words.indexOf("their")).isLessThan(words.indexOf("they"))
    }

    @Test
    fun `verbatim is offered for unknown input`() = runTest {
        val result = (provider.suggest(request("xyz")) as com.bornomala.keyboard.core.result.AppResult.Success).data
        val verbatim = result.firstOrNull { it.source == SuggestionSource.VERBATIM }
        assertThat(verbatim).isNotNull()
        assertThat(verbatim!!.word).isEqualTo("xyz")
        assertThat(verbatim.isExactMatch).isTrue()
    }

    @Test
    fun `learned word outranks dictionary words of same prefix`() = runTest {
        // Learn "thermos" several times; it is not in the bundled dictionary.
        repeat(5) { provider.learn("thermos", previousWord = "", secondPreviousWord = "", language = SuggestionLanguage.ENGLISH) }
        val result = (provider.suggest(request("the", limit = 5)) as com.bornomala.keyboard.core.result.AppResult.Success).data
        assertThat(result.first().word).isEqualTo("thermos")
        assertThat(result.first().source).isEqualTo(SuggestionSource.USER_DICTIONARY)
    }

    @Test
    fun `learning increments frequency`() = runTest {
        provider.learn("hello", previousWord = "", secondPreviousWord = "", language = SuggestionLanguage.ENGLISH)
        provider.learn("hello", previousWord = "", secondPreviousWord = "", language = SuggestionLanguage.ENGLISH)
        val entry = dao.findExact("hello", SuggestionLanguage.ENGLISH.code)
        assertThat(entry).isNotNull()
        assertThat(entry!!.frequency).isEqualTo(2)
    }

    @Test
    fun `learning lowercases english words`() = runTest {
        provider.learn("Hello", previousWord = "", secondPreviousWord = "", language = SuggestionLanguage.ENGLISH)
        assertThat(dao.findExact("hello", SuggestionLanguage.ENGLISH.code)).isNotNull()
        assertThat(dao.findExact("Hello", SuggestionLanguage.ENGLISH.code)).isNull()
    }

    @Test
    fun `empty word cannot be learned`() = runTest {
        val result = provider.learn("   ", previousWord = "", secondPreviousWord = "", language = SuggestionLanguage.ENGLISH)
        assertThat(result).isInstanceOf(com.bornomala.keyboard.core.result.AppResult.Failure::class.java)
    }

    @Test
    fun `next word prediction uses learned bigrams`() = runTest {
        provider.learn("morning", previousWord = "good", secondPreviousWord = "", language = SuggestionLanguage.ENGLISH)
        provider.learn("morning", previousWord = "good", secondPreviousWord = "", language = SuggestionLanguage.ENGLISH)
        provider.learn("night", previousWord = "good", secondPreviousWord = "", language = SuggestionLanguage.ENGLISH)

        val result = (provider.suggest(request(current = "", previous = "good")) as com.bornomala.keyboard.core.result.AppResult.Success).data
        assertThat(result).isNotEmpty()
        assertThat(result.first().word).isEqualTo("morning")
        assertThat(result.map { it.word }).contains("night")
    }

    @Test
    fun `trigram context outranks bigram for the same previous word`() = runTest {
        provider.learn("to", previousWord = "want", secondPreviousWord = "i", language = SuggestionLanguage.ENGLISH)
        provider.learn("to", previousWord = "want", secondPreviousWord = "i", language = SuggestionLanguage.ENGLISH)
        provider.learn("go", previousWord = "want", secondPreviousWord = "", language = SuggestionLanguage.ENGLISH)
        val req = SuggestionRequest(
            currentWord = "",
            previousWord = "want",
            secondPreviousWord = "i",
            language = SuggestionLanguage.ENGLISH,
            limit = 5,
        )
        val result = (provider.suggest(req) as com.bornomala.keyboard.core.result.AppResult.Success).data
        assertThat(result.first().word).isEqualTo("to")
        assertThat(result.map { it.word }).contains("go")
    }

    @Test
    fun `next word with no previous word falls back to frequent words`() = runTest {
        // With no context the strip should still offer something useful (generic top words),
        // never go blank — drawn from the most frequent dictionary entries.
        val result = (provider.suggest(request(current = "", previous = "")) as com.bornomala.keyboard.core.result.AppResult.Success).data
        assertThat(result).isNotEmpty()
        assertThat(result.first().word).isEqualTo("the") // highest frequency in the fixture
    }

    @Test
    fun `transposed typo is corrected and flagged for auto-correct`() = runTest {
        // "teh" -> "the" via an adjacent transposition; "teh" is not a dictionary word.
        val result = (provider.suggest(request("teh", limit = 5)) as com.bornomala.keyboard.core.result.AppResult.Success).data
        assertThat(result.first().word).isEqualTo("the")
        assertThat(result.first().source).isEqualTo(SuggestionSource.CORRECTION)
        assertThat(result.first().autoCorrect).isTrue()
    }

    @Test
    fun `a known word is never auto-corrected`() = runTest {
        // "the" is a real word; nothing in the result should be flagged for auto-correct.
        val result = (provider.suggest(request("the", limit = 5)) as com.bornomala.keyboard.core.result.AppResult.Success).data
        assertThat(result.none { it.autoCorrect }).isTrue()
    }

    @Test
    fun `respects the requested limit`() = runTest {
        val result = (provider.suggest(request("t", limit = 2)) as com.bornomala.keyboard.core.result.AppResult.Success).data
        assertThat(result.size).isAtMost(2)
    }
}
