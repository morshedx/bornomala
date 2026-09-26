package com.bornomala.keyboard.suggestions.data.dictionary

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily loads [BanglaSuffixes] from riti's `suffix.json` asset on first use, off the main
 * thread, and caches it; a failed load degrades to [BanglaSuffixes.EMPTY].
 */
@Singleton
class BanglaSuffixRepository @Inject constructor(
    private val source: DictionarySource,
    private val dispatchers: DispatcherProvider,
) {

    @Volatile
    private var cached: BanglaSuffixes? = null
    private val mutex = Mutex()

    suspend fun suffixes(): BanglaSuffixes {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            val built = withContext(dispatchers.default) {
                runCatching { BanglaSuffixes.build(source.suffixLines()) }
                    .getOrDefault(BanglaSuffixes.EMPTY)
            }
            cached = built
            built
        }
    }
}
