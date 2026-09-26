package com.bornomala.keyboard.suggestions.data.local

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.core.result.appRunCatching
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import dagger.Lazy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read/write access to the user's roman -> word picks.
 *
 * Picks are consulted for every base + suffix reading of every keystroke, so they are mirrored
 * in memory: the table is loaded once (it holds only words the user explicitly chose, so it stays
 * small) and every write goes to both the map and Room. The DAO is [Lazy] so the database opens
 * only on first real use; every call runs on [DispatcherProvider.io] and returns [AppResult], so
 * a SQLite error never reaches the input path.
 */
@Singleton
class RomanPickRepository @Inject constructor(
    private val daoLazy: Lazy<RomanPickDao>,
    private val dispatchers: DispatcherProvider,
) {

    private val dao: RomanPickDao get() = daoLazy.get()

    /** `"<lang>\u0000<roman>"` -> word; null until first loaded. */
    @Volatile
    private var cache: ConcurrentHashMap<String, String>? = null
    private val mutex = Mutex()

    suspend fun find(roman: String, language: SuggestionLanguage): AppResult<String?> =
        loaded().let { result ->
            when (result) {
                is AppResult.Success -> AppResult.Success(result.data[cacheKey(roman, language)])
                is AppResult.Failure -> result
            }
        }

    suspend fun record(
        roman: String,
        word: String,
        language: SuggestionLanguage,
        now: Long = System.currentTimeMillis(),
    ): AppResult<Unit> = withContext(dispatchers.io) {
        appRunCatching {
            dao.upsert(RomanPickEntity(roman, language.code, word, now))
            cache?.put(cacheKey(roman, language), word)
            Unit
        }
    }

    suspend fun exportAll(): AppResult<List<RomanPickEntity>> = withContext(dispatchers.io) {
        appRunCatching { dao.getAll() }
    }

    suspend fun replaceAll(picks: List<RomanPickEntity>): AppResult<Unit> = withContext(dispatchers.io) {
        appRunCatching {
            dao.replaceAll(picks)
            cache = null // reload lazily from the restored table
        }
    }

    private suspend fun loaded(): AppResult<ConcurrentHashMap<String, String>> {
        cache?.let { return AppResult.Success(it) }
        return mutex.withLock {
            cache?.let { return AppResult.Success(it) }
            withContext(dispatchers.io) {
                appRunCatching {
                    val map = ConcurrentHashMap<String, String>()
                    for (pick in dao.getAll()) map[pick.lang + SEPARATOR + pick.roman] = pick.word
                    cache = map
                    map
                }
            }
        }
    }

    private fun cacheKey(roman: String, language: SuggestionLanguage) = language.code + SEPARATOR + roman

    private companion object {
        const val SEPARATOR = '\u0000'
    }
}
