package com.bornomala.keyboard.suggestions.util

import com.bornomala.keyboard.suggestions.data.dictionary.DictionarySource
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage

/**
 * Test [DictionarySource] that serves dictionary lines from in-memory maps, avoiding
 * a real Android Context / asset reads in plain unit tests.
 */
class InMemoryDictionarySource(
    private val data: Map<SuggestionLanguage, List<String>>,
) : DictionarySource {
    override fun linesFor(language: SuggestionLanguage): Sequence<String> =
        (data[language] ?: emptyList()).asSequence()
}
