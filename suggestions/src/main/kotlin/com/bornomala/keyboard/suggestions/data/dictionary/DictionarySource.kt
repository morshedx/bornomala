package com.bornomala.keyboard.suggestions.data.dictionary

import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage

/**
 * Supplies the raw lines for a language's bundled frequency dictionary.
 *
 * Abstracted so the heavy asset read can be swapped for an in-memory source in unit
 * tests (avoiding a real Context) while production reads from `assets/`.
 */
interface DictionarySource {
    /**
     * Returns the dictionary lines for [language] as a sequence so the loader can
     * stream-parse without holding the whole file plus a List in memory at once.
     * The returned sequence is consumed exactly once.
     */
    fun linesFor(language: SuggestionLanguage): Sequence<String>
}
