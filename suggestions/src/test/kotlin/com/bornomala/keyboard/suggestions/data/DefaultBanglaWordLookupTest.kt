package com.bornomala.keyboard.suggestions.data

import com.bornomala.keyboard.suggestions.data.dictionary.BanglaAutocorrectRepository
import com.bornomala.keyboard.suggestions.data.dictionary.BanglaSuffixRepository
import com.bornomala.keyboard.suggestions.data.dictionary.DictionarySource
import com.bornomala.keyboard.suggestions.data.local.RomanPickDao
import com.bornomala.keyboard.suggestions.data.local.RomanPickEntity
import com.bornomala.keyboard.suggestions.data.local.RomanPickRepository
import com.bornomala.keyboard.suggestions.domain.AvroRenderer
import com.bornomala.keyboard.suggestions.domain.model.BanglaWordMatch
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.util.TestDispatcherProvider
import com.bornomala.keyboard.suggestions.util.lazyOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultBanglaWordLookupTest {

    private val dispatchers = TestDispatcherProvider()

    private val source = object : DictionarySource {
        override fun linesFor(language: SuggestionLanguage) = emptySequence<String>()
        override fun bigramLinesFor(language: SuggestionLanguage) = emptySequence<String>()
        override fun loanwordLines() = sequenceOf("computer\tকম্পিউটার", "bus\tবাস")
        override fun suffixLines() = sequenceOf("""  "er": "ের",""", """  "e": "ে"""")
    }

    private val dao = InMemoryRomanPickDao()
    private val picks = RomanPickRepository(lazyOf(dao), dispatchers)

    private val lookup = DefaultBanglaWordLookup(
        picks = picks,
        autocorrect = BanglaAutocorrectRepository(source, AvroRenderer { it }, dispatchers),
        suffixes = BanglaSuffixRepository(source, dispatchers),
    )

    @Test
    fun `bundled loanword resolves, and carries through a suffix`() = runTest {
        assertThat(lookup.lookup("computer")).isEqualTo(BanglaWordMatch("কম্পিউটার", learned = false))
        assertThat(lookup.lookup("computerer")).isEqualTo(BanglaWordMatch("কম্পিউটারের", learned = false))
    }

    @Test
    fun `a pick wins over the bundled entry and carries through a suffix`() = runTest {
        lookup.learnPick("bus", "বাস্")
        assertThat(lookup.lookup("bus")).isEqualTo(BanglaWordMatch("বাস্", learned = true))
        assertThat(lookup.lookup("buser")).isEqualTo(BanglaWordMatch("বাস্ের", learned = true))
    }

    @Test
    fun `picks persist and survive a fresh repository`() = runTest {
        lookup.learnPick("chair", "চেয়ার")
        assertThat(dao.getAll().single().word).isEqualTo("চেয়ার")
        val reloaded = RomanPickRepository(lazyOf(dao), dispatchers)
        assertThat(reloaded.find("chair", SuggestionLanguage.BANGLA).let { (it as com.bornomala.keyboard.core.result.AppResult.Success).data })
            .isEqualTo("চেয়ার")
    }

    @Test
    fun `short bases are not carried through a suffix`() = runTest {
        lookup.learnPick("ki", "কী")
        assertThat(lookup.lookup("kie")).isNull()
    }

    @Test
    fun `unknown spellings have no match`() = runTest {
        assertThat(lookup.lookup("ami")).isNull()
        assertThat(lookup.lookup("")).isNull()
    }

    private class InMemoryRomanPickDao : RomanPickDao {
        private val rows = LinkedHashMap<Pair<String, String>, RomanPickEntity>()
        override suspend fun find(roman: String, lang: String) = rows[roman to lang]?.word
        override suspend fun upsert(pick: RomanPickEntity) { rows[pick.roman to pick.lang] = pick }
        override suspend fun clear() = rows.clear()
        override suspend fun getAll() = rows.values.toList()
        override suspend fun insertAll(picks: List<RomanPickEntity>) = picks.forEach { upsert(it) }
    }
}
