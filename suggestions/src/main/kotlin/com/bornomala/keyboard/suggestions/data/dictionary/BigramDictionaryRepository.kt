package com.bornomala.keyboard.suggestions.data.dictionary

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily loads and caches the per-language [BigramDictionary] seed.
 *
 * Mirrors [FrequencyDictionaryRepository]: the first request for a language parses its
 * bundled bigram asset off the main thread and caches the immutable result for the
 * process lifetime, guarded by a [Mutex] so concurrent first-touch parses run once.
 * A missing/empty seed degrades to [BigramDictionary.EMPTY] rather than failing.
 */
@Singleton
class BigramDictionaryRepository @Inject constructor(
    private val source: DictionarySource,
    private val dispatchers: DispatcherProvider,
) {

    private val cache = HashMap<SuggestionLanguage, BigramDictionary>()
    private val mutex = Mutex()

    suspend fun get(language: SuggestionLanguage): BigramDictionary {
        cache[language]?.let { return it }
        return mutex.withLock {
            cache[language]?.let { return it }
            val built = withContext(dispatchers.default) {
                runCatching {
                    BigramDictionary.build(
                        lines = source.bigramLinesFor(language),
                        lowercase = language == SuggestionLanguage.ENGLISH,
                    )
                }.getOrDefault(BigramDictionary.EMPTY)
            }
            cache[language] = built
            built
        }
    }
}
