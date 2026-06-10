package com.bornomala.keyboard.suggestions.util

import com.bornomala.keyboard.suggestions.data.dictionary.DictionarySource
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage

/**
 * Test [DictionarySource] that serves dictionary lines from in-memory maps, avoiding
 * a real Android Context / asset reads in plain unit tests.
 */
class InMemoryDictionarySource(
    private val data: Map<SuggestionLanguage, List<String>>,
    private val bigrams: Map<SuggestionLanguage, List<String>> = emptyMap(),
    private val phonetic: Map<SuggestionLanguage, List<String>> = emptyMap(),
) : DictionarySource {
    override fun linesFor(language: SuggestionLanguage): Sequence<String> =
        (data[language] ?: emptyList()).asSequence()

    override fun bigramLinesFor(language: SuggestionLanguage): Sequence<String> =
        (bigrams[language] ?: emptyList()).asSequence()

    override fun phoneticLinesFor(language: SuggestionLanguage): Sequence<String> =
        (phonetic[language] ?: emptyList()).asSequence()
}
