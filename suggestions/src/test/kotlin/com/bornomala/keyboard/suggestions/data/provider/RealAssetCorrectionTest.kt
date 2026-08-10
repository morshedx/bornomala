package com.bornomala.keyboard.suggestions.data.provider

import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.suggestions.data.dictionary.BigramDictionaryRepository
import com.bornomala.keyboard.suggestions.data.dictionary.FrequencyDictionaryRepository
import com.bornomala.keyboard.suggestions.data.dictionary.OffensiveWordRepository
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.domain.model.SuggestionRequest
import com.bornomala.keyboard.suggestions.domain.model.SuggestionSource
import com.bornomala.keyboard.suggestions.util.FakeUserDictionaryDao
import com.bornomala.keyboard.suggestions.util.InMemoryDictionarySource
import com.bornomala.keyboard.suggestions.util.TestDispatcherProvider
import com.bornomala.keyboard.suggestions.util.lazyOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

/**
 * Drives the real bundled English asset files through [OfflineProvider] to prove the shipped
 * model corrects common typos end-to-end (not just the synthetic fixtures).
 */
class RealAssetCorrectionTest {

    private val dispatchers = TestDispatcherProvider()

    private fun providerFromAssets(): OfflineProvider {
        val assetDir = File("src/main/assets/dictionaries")
        val freq = File(assetDir, "en_frequency.txt").readLines()
        val bigrams = File(assetDir, "en_bigrams.txt").readLines()
        val source = InMemoryDictionarySource(
            data = mapOf(SuggestionLanguage.ENGLISH to freq),
            bigrams = mapOf(SuggestionLanguage.ENGLISH to bigrams),
        )
        val dao = FakeUserDictionaryDao()
        val userRepo = UserDictionaryRepository(lazyOf(dao), dispatchers)
        return OfflineProvider(
            FrequencyDictionaryRepository(source, dispatchers),
            BigramDictionaryRepository(source, dispatchers),
            userRepo,
            OffensiveWordRepository(source, dispatchers),
        )
    }

    private fun req(word: String) = SuggestionRequest(
        currentWord = word,
        previousWord = "",
        language = SuggestionLanguage.ENGLISH,
        limit = 6,
    )

    @Test
    fun `teh corrects to the and is flagged for auto-correct`() = runTest {
        val provider = providerFromAssets()
        val result = (provider.suggest(req("teh")) as AppResult.Success).data
        assertThat(result.first().word).isEqualTo("the")
        assertThat(result.first().source).isEqualTo(SuggestionSource.CORRECTION)
        assertThat(result.first().autoCorrect).isTrue()
    }

    @Test
    fun `recieve corrects to receive`() = runTest {
        val provider = providerFromAssets()
        val result = (provider.suggest(req("recieve")) as AppResult.Success).data
        assertThat(result.map { it.word }).contains("receive")
    }

    private fun phoneticRepoFromAssets(): com.bornomala.keyboard.suggestions.data.dictionary.BanglaPhoneticRepository {
        val lines = File("src/main/assets/dictionaries/bn_phonetic.txt").readLines()
        val source = InMemoryDictionarySource(
            data = emptyMap(),
            phonetic = mapOf(SuggestionLanguage.BANGLA to lines),
        )
        return com.bornomala.keyboard.suggestions.data.dictionary.BanglaPhoneticRepository(source, dispatchers)
    }

    @Test
    fun `chara resolves to ছাড়া via the real bangla phonetic index`() = runTest {
        val engine = com.bornomala.keyboard.suggestions.data.DefaultSuggestionEngine(
            providers = setOf(providerFromAssets()),
            dispatchers = dispatchers,
            banglaPhonetic = phoneticRepoFromAssets(),
            userDictionary = com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository(
                com.bornomala.keyboard.suggestions.util.lazyOf(
                    com.bornomala.keyboard.suggestions.util.FakeUserDictionaryDao(),
                ),
                dispatchers,
            ),
        )
        val result = engine.banglaPhoneticCandidates("chara", 5)
        assertThat(result).isNotEmpty()
        assertThat(result.first()).isEqualTo("ছাড়া")
    }

    @Test
    fun `engine path preserves the auto-correct flag`() = runTest {
        val engine = com.bornomala.keyboard.suggestions.data.DefaultSuggestionEngine(
            providers = setOf(providerFromAssets()),
            dispatchers = dispatchers,
            banglaPhonetic = phoneticRepoFromAssets(),
            userDictionary = com.bornomala.keyboard.suggestions.data.local.UserDictionaryRepository(
                com.bornomala.keyboard.suggestions.util.lazyOf(
                    com.bornomala.keyboard.suggestions.util.FakeUserDictionaryDao(),
                ),
                dispatchers,
            ),
        )
        val result = engine.getSuggestions(req("teh"))
        assertThat(result.first().word).isEqualTo("the")
        assertThat(result.first().autoCorrect).isTrue()
    }
}
