package com.bornomala.keyboard.suggestions.data

import com.bornomala.keyboard.suggestions.data.dictionary.BanglaPhoneticKey
import com.bornomala.keyboard.suggestions.data.dictionary.BanglaPhoneticRepository
import com.bornomala.keyboard.suggestions.data.dictionary.BanglaSuffixRepository
import com.bornomala.keyboard.suggestions.data.dictionary.DictionarySource
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.util.FakeUserDictionaryDao
import com.bornomala.keyboard.suggestions.util.TestDispatcherProvider
import com.bornomala.keyboard.suggestions.util.lazyOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Suggest-only candidates — riti dictionary words and base + suffix joins — are offered after
 * the trusted words and are excluded from [com.bornomala.keyboard.suggestions.domain.model.BanglaCandidates.trustedCount].
 */
class BanglaSuggestOnlyCandidatesTest {

    private val dispatchers = TestDispatcherProvider()

    private val source = object : DictionarySource {
        override fun linesFor(language: SuggestionLanguage) = emptySequence<String>()
        override fun bigramLinesFor(language: SuggestionLanguage) = emptySequence<String>()
        override fun phoneticLinesFor(language: SuggestionLanguage) = sequenceOf(
            "${key("boi")}\tবই",
            "${key("bari")}\tবাড়ি\tবারি",
            "${key("abaca")}\t\tআবাছা",
        )
        override fun suffixLines() = sequenceOf(
            """  "gulo": "গুলো",""",
            """  "te": "তে",""",
            """  "e": "ে"""",
        )
    }

    private val engine = DefaultSuggestionEngine(
        providers = emptySet(),
        dispatchers = dispatchers,
        banglaPhonetic = BanglaPhoneticRepository(source, dispatchers),
        userDictionary = UserDictionaryRepository(lazyOf(FakeUserDictionaryDao()), dispatchers),
        suffixes = BanglaSuffixRepository(source, dispatchers),
    )

    private fun key(roman: String) = BanglaPhoneticKey.romanKey(roman)

    @Test
    fun `trusted words lead and dictionary-only words follow`() = runTest {
        val result = engine.banglaPhoneticCandidates("bari", 6)
        assertThat(result.words).containsExactly("বাড়ি", "বারি").inOrder()
        assertThat(result.trustedCount).isEqualTo(1)
    }

    @Test
    fun `a dictionary-only word is offered but not trusted`() = runTest {
        val result = engine.banglaPhoneticCandidates("abaca", 6)
        assertThat(result.words).containsExactly("আবাছা")
        assertThat(result.trustedCount).isEqualTo(0)
    }

    @Test
    fun `an inflected word is built from its base and suffix`() = runTest {
        val result = engine.banglaPhoneticCandidates("boigulo", 6)
        assertThat(result.words).contains("বইগুলো")
        assertThat(result.trustedCount).isEqualTo(0)
    }

    @Test
    fun `suffix joining applies the ya rule`() = runTest {
        assertThat(engine.banglaPhoneticCandidates("boie", 6).words).contains("বইয়ে")
        assertThat(engine.banglaPhoneticCandidates("barite", 6).words).contains("বাড়িতে")
    }
}
