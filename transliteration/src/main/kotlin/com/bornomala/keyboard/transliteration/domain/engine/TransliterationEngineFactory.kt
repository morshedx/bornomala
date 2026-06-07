package com.bornomala.keyboard.transliteration.domain.engine

/**
 * Creates fresh [TransliterationEngine] instances.
 *
 * The engine is stateful (it owns a per-word composition buffer) and not thread safe, so it
 * is intentionally NOT a shared singleton. The IME obtains its own instance through this
 * factory and drives it from its single input thread; tests create throwaway instances. The
 * factory itself can be a singleton and is cheap — engine construction only reads the cached
 * rule table.
 */
fun interface TransliterationEngineFactory {
    fun create(): TransliterationEngine
}
