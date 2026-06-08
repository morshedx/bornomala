package com.bornomala.keyboard.suggestions.data.local

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.core.result.appRunCatching
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import dagger.Lazy
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read/learn access to the on-device user dictionary, sitting between the provider
 * and the Room [UserDictionaryDao].
 *
 * The DAO is injected via [dagger.Lazy] so the Room database is not created until the
 * first read or learn actually happens — protecting IME cold start. All operations
 * run on [DispatcherProvider.io] and return [AppResult] so the provider never sees a
 * thrown SQLite exception on the keystroke path.
 *
 * Writes are intentionally single-statement atomic increments ([UserDictionaryDao.learnWord]);
 * the caller (provider/engine) is responsible for *when* to learn (on word-commit,
 * not per character) so disk writes stay coalesced and off the input path.
 */
@Singleton
class UserDictionaryRepository @Inject constructor(
    private val daoLazy: Lazy<UserDictionaryDao>,
    private val dispatchers: DispatcherProvider,
) {

    private val dao: UserDictionaryDao get() = daoLazy.get()

    /** Prefix-completion candidates from learned words, ranked frequency then recency. */
    suspend fun queryByPrefix(
        language: SuggestionLanguage,
        prefix: String,
        limit: Int,
    ): AppResult<List<UserDictionaryEntity>> = withContext(dispatchers.io) {
        appRunCatching { dao.queryByPrefix(language.code, prefix, limit) }
    }

    /** Next-word candidates learned to follow [previousWord]. */
    suspend fun queryNextWord(
        language: SuggestionLanguage,
        previousWord: String,
        limit: Int,
    ): AppResult<List<UserDictionaryEntity>> = withContext(dispatchers.io) {
        appRunCatching { dao.queryNextWord(language.code, previousWord, limit) }
    }

    /**
     * Learns [word]: atomically inserts or increments its frequency and refreshes
     * recency and the last-seen previous word. [now] is injectable for deterministic
     * tests; production passes the wall clock.
     */
    suspend fun learn(
        word: String,
        previousWord: String,
        language: SuggestionLanguage,
        now: Long = System.currentTimeMillis(),
    ): AppResult<Unit> = withContext(dispatchers.io) {
        appRunCatching { dao.learnWord(word, language.code, previousWord, now) }
    }

    /** Records a learned next-word association: [context] (preceding word(s)) -> [word]. */
    suspend fun learnNgram(
        language: SuggestionLanguage,
        context: String,
        word: String,
        now: Long = System.currentTimeMillis(),
    ): AppResult<Unit> = withContext(dispatchers.io) {
        appRunCatching { dao.learnNgram(context, word, language.code, now) }
    }

    /** Next-word candidates learned to follow [context] (a bigram or trigram context). */
    suspend fun queryNgram(
        language: SuggestionLanguage,
        context: String,
        limit: Int,
    ): AppResult<List<LearnedNgramEntity>> = withContext(dispatchers.io) {
        appRunCatching { dao.queryNgram(language.code, context, limit) }
    }

    /** Exact lookup; null when the word has never been learned in [language]. */
    suspend fun findExact(
        word: String,
        language: SuggestionLanguage,
    ): AppResult<UserDictionaryEntity?> = withContext(dispatchers.io) {
        appRunCatching { dao.findExact(word, language.code) }
    }

    /** Removes a single learned word. */
    suspend fun forget(
        word: String,
        language: SuggestionLanguage,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        appRunCatching { dao.delete(word, language.code) }
    }

    /** Clears all learned words (settings: reset learned words). */
    suspend fun clearAll(): AppResult<Unit> = withContext(dispatchers.io) {
        appRunCatching { dao.clear() }
    }
}
