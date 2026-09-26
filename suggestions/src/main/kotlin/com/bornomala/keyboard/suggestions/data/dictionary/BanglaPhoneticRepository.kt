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
 * spell to it: trusted corpus words best-first by frequency, then suggest-only words from
 * OpenBangla riti's dictionary. It is held as a compact [PhoneticIndex]. The first lookup parses
 * the asset off the main thread and caches it for the process lifetime; a missing index degrades
 * to [PhoneticIndex.EMPTY].
 */
@Singleton
class BanglaPhoneticRepository @Inject constructor(
    private val source: DictionarySource,
    private val dispatchers: DispatcherProvider,
) {

    @Volatile
    private var cached: PhoneticIndex? = null
    private val mutex = Mutex()

    /** Trusted words matching the phonetic key of [roman], best-first; empty if none. */
    suspend fun candidates(roman: String, limit: Int): List<String> =
        candidatesForKey(BanglaPhoneticKey.romanKey(roman), limit)

    /** As [candidates], for a phonetic key the caller has already computed. */
    suspend fun candidatesForKey(key: String, limit: Int): List<String> = hitsForKey(key, limit).trusted

    /** Trusted and suggest-only words for [key], each group best-first. */
    suspend fun hitsForKey(key: String, limit: Int): PhoneticHits {
        if (key.isEmpty()) return PhoneticHits.EMPTY
        return table().lookup(key, limit)
    }

    private suspend fun table(): PhoneticIndex {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            val built = withContext(dispatchers.default) {
                runCatching {
                    PhoneticIndex.build(source.phoneticLinesFor(SuggestionLanguage.BANGLA))
                }.getOrDefault(PhoneticIndex.EMPTY)
            }
            cached = built
            built
        }
    }
}
