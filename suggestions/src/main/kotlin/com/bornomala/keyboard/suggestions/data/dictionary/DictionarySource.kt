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

    /**
     * Returns the bundled bigram seed lines for [language] (`prev<TAB>next1 next2 …`),
     * used to seed next-word prediction before on-device learning kicks in. May yield
     * nothing if no seed ships for the language.
     */
    fun bigramLinesFor(language: SuggestionLanguage): Sequence<String>

    /**
     * Returns the Bangla phonetic-index lines (`key<TAB>word1 word2 …`), mapping an
     * ambiguity-collapsed roman key to real Bangla words by frequency. Only Bangla ships one;
     * other languages yield nothing.
     */
    fun phoneticLinesFor(language: SuggestionLanguage): Sequence<String> = emptySequence()

    /**
     * Returns the bundled offensive-word blocklist for [language], one word per line. Used to
     * keep profanity/slurs out of suggestions and auto-correction. May yield nothing.
     */
    fun offensiveLinesFor(language: SuggestionLanguage): Sequence<String> = emptySequence()
}
