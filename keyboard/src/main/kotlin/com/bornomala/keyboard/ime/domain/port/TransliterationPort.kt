package com.bornomala.keyboard.ime.domain.port

/**
 * Inbound port the keyboard uses to talk to the Bangla transliteration engine
 * (implemented by the :transliteration module). Defining the contract here — inside the
 * consumer — inverts the dependency: the keyboard depends only on this interface, and the
 * app's DI graph binds a thin adapter onto the real `TransliterationEngine`. This keeps
 * :keyboard independently compilable and unit-testable with a fake.
 *
 * Implementations are stateful (they own a per-word input buffer). All methods are
 * synchronous and must be cheap: they sit on the per-keystroke hot path and run on the
 * main thread, so engines must use a pre-built trie/lookup table — no regex, no
 * allocation-heavy work, no I/O.
 */
interface TransliterationPort {

    /**
     * Feeds the running latin buffer for the current word and returns the best Bangla
     * rendering of the whole buffer so far.
     *
     * @param buffer the accumulated latin characters of the in-progress word.
     * @return the transliterated Bangla string for [buffer].
     */
    fun transliterate(buffer: String): String

    /**
     * Optional alternative renderings for the current buffer (e.g. ambiguous vowels),
     * highest-confidence first. May be empty. Used to populate the suggestion bar in
     * Bangla mode. Must not allocate on every keystroke beyond the returned list.
     */
    fun candidates(buffer: String): List<String> = emptyList()
}
