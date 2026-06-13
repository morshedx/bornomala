package com.bornomala.keyboard.suggestions.data.dictionary

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily loads and caches the per-language offensive-word blocklist used to keep profanity
 * and slurs out of suggestions and auto-correction.
 *
 * Words are normalized the same way the [OfflineProvider] normalizes candidates (English is
 * lower-cased; Bangla is matched verbatim) so membership checks are O(1). The asset is read
 * off the main thread on first use and cached for the process lifetime; a missing or
 * unreadable blocklist degrades to an empty set (filter becomes a no-op for that language).
 */
@Singleton
class OffensiveWordRepository @Inject constructor(
    private val source: DictionarySource,
    private val dispatchers: DispatcherProvider,
) {
    private val cache = HashMap<SuggestionLanguage, Set<String>>()
    private val mutex = Mutex()

    /** Returns the blocklist for [language], building and caching it on first use. */
    suspend fun get(language: SuggestionLanguage): Set<String> {
        cache[language]?.let { return it }
        return mutex.withLock {
            cache[language]?.let { return it }
            val built = withContext(dispatchers.default) { load(language) }
            cache[language] = built
            built
        }
    }

    private fun load(language: SuggestionLanguage): Set<String> = try {
        val set = HashSet<String>(512)
        for (raw in source.offensiveLinesFor(language)) {
            val word = normalize(raw, language)
            if (word.isNotEmpty() && !word.startsWith("#")) set.add(word)
        }
        set
    } catch (_: java.io.IOException) {
        emptySet()
    }

    private fun normalize(word: String, language: SuggestionLanguage): String =
        when (language) {
            SuggestionLanguage.ENGLISH -> word.trim().lowercase()
            SuggestionLanguage.BANGLA -> word.trim()
        }
}
