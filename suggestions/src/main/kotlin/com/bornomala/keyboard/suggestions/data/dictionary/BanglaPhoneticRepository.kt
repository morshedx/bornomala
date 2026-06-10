package com.bornomala.keyboard.suggestions.data.dictionary

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily loads the bundled Bangla phonetic index and resolves roman input to real Bangla words.
 *
 * The index (`bn_phonetic.txt`) maps an ambiguity-collapsed roman key to the Bangla words that
 * spell to it, best-first by frequency — the same `key<TAB>word…` shape as the bigram seed, so
 * it reuses [BigramDictionary] as the in-memory table (loaded with `lowercase = false` to keep
 * the Bangla word values intact). The first lookup parses the asset off the main thread and
 * caches it for the process lifetime; a missing index degrades to [BigramDictionary.EMPTY].
 */
@Singleton
class BanglaPhoneticRepository @Inject constructor(
    private val source: DictionarySource,
    private val dispatchers: DispatcherProvider,
) {

    @Volatile
    private var cached: BigramDictionary? = null
    private val mutex = Mutex()

    /** Real Bangla words matching the phonetic key of [roman], best-first; empty if none. */
    suspend fun candidates(roman: String, limit: Int): List<String> {
        val key = BanglaPhoneticKey.romanKey(roman)
        if (key.isEmpty()) return emptyList()
        return table().nextWords(key, limit)
    }

    private suspend fun table(): BigramDictionary {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            val built = withContext(dispatchers.default) {
                runCatching {
                    BigramDictionary.build(
                        lines = source.phoneticLinesFor(SuggestionLanguage.BANGLA),
                        lowercase = false,
                    )
                }.getOrDefault(BigramDictionary.EMPTY)
            }
            cached = built
            built
        }
    }
}
