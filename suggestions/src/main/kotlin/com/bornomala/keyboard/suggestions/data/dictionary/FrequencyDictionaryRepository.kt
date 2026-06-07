package com.bornomala.keyboard.suggestions.data.dictionary

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily loads and caches the per-language [FrequencyDictionary].
 *
 * The first request for a language parses its asset off the main thread (on
 * [DispatcherProvider.default]) and caches the immutable result for the process
 * lifetime. A per-repository [Mutex] guards the cache so concurrent first-touch
 * requests for the same language parse only once. Nothing is loaded eagerly, keeping
 * IME cold start fast.
 */
@Singleton
class FrequencyDictionaryRepository @Inject constructor(
    private val source: DictionarySource,
    private val dispatchers: DispatcherProvider,
) {

    private val cache = HashMap<SuggestionLanguage, FrequencyDictionary>()
    private val mutex = Mutex()

    /** Returns the dictionary for [language], building and caching it on first use. */
    suspend fun get(language: SuggestionLanguage): FrequencyDictionary {
        cache[language]?.let { return it }
        return mutex.withLock {
            cache[language]?.let { return it }
            val built = withContext(dispatchers.default) {
                FrequencyDictionary.build(
                    lines = source.linesFor(language),
                    lowercase = language == SuggestionLanguage.ENGLISH,
                )
            }
            cache[language] = built
            built
        }
    }

    /** True if the dictionary for [language] has already been parsed and cached. */
    fun isLoaded(language: SuggestionLanguage): Boolean = cache.containsKey(language)
}
