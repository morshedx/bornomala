package com.bornomala.keyboard.suggestions.data.local

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.core.result.appRunCatching
import com.bornomala.keyboard.suggestions.data.dictionary.BanglaPhoneticKey
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import dagger.Lazy
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
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

    /** Languages whose phonetic-key backfill has already run in this process. */
    private val backfilled = Collections.newSetFromMap(ConcurrentHashMap<SuggestionLanguage, Boolean>())

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
        appRunCatching { dao.learnWord(word, language.code, previousWord, now, phoneticKeyOf(word, language)) }
    }

    /**
     * Learned words whose ambiguity-collapsed phonetic key is [phoneticKey], best-first. This is
     * how roman Avro input reaches words the user taught the keyboard rather than only the
     * bundled index — the reason repeatedly typing a word eventually makes it the auto-pick.
     */
    suspend fun queryByPhoneticKey(
        language: SuggestionLanguage,
        phoneticKey: String,
        limit: Int,
    ): AppResult<List<UserDictionaryEntity>> = withContext(dispatchers.io) {
        appRunCatching { dao.queryByPhoneticKey(language.code, phoneticKey, limit) }
    }

    /**
     * Fills in phonetic keys for rows learned before the column existed (schema v2 and the
     * backup restore path, which carries no key for older archives). Runs at most once per
     * process and is bounded, so it can be fired from the query path without becoming a
     * repeated scan; failures are swallowed because a missing key only costs a suggestion.
     */
    suspend fun backfillPhoneticKeys(language: SuggestionLanguage): AppResult<Unit> {
        // Cheap guard first: this is called per keystroke, so the steady state must not even
        // switch dispatchers, let alone touch the database.
        if (language in backfilled) return AppResult.Success(Unit)
        return withContext(dispatchers.io) {
            appRunCatching {
                if (!backfilled.add(language)) return@appRunCatching
                val pending = dao.wordsMissingPhoneticKey(language.code, BACKFILL_LIMIT)
                for (entry in pending) {
                    val key = phoneticKeyOf(entry.word, language)
                    if (key.isNotEmpty()) dao.setPhoneticKey(entry.word, language.code, key)
                }
            }
        }
    }

    /** Bangla words carry a phonetic key; English resolves by spelling, so its key stays empty. */
    private fun phoneticKeyOf(word: String, language: SuggestionLanguage): String =
        if (language == SuggestionLanguage.BANGLA) BanglaPhoneticKey.banglaKey(word) else ""

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

    // --- backup export / import ---------------------------------------------------------

    /** All learned words + n-grams, for backup export. */
    suspend fun exportAll(): AppResult<Pair<List<UserDictionaryEntity>, List<LearnedNgramEntity>>> =
        withContext(dispatchers.io) {
            appRunCatching { dao.getAllWords() to dao.getAllNgrams() }
        }

    /** Replaces the entire dictionary with backup contents (restore). */
    suspend fun replaceAll(
        words: List<UserDictionaryEntity>,
        ngrams: List<LearnedNgramEntity>,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        appRunCatching {
            // A restored archive may predate the phonetic key, so recompute rather than trust it.
            dao.replaceAll(
                words.map { entry ->
                    val language = SuggestionLanguage.fromCode(entry.lang)
                    if (language == null) entry else entry.copy(phoneticKey = phoneticKeyOf(entry.word, language))
                },
                ngrams,
            )
            backfilled.clear()
        }
    }

    private companion object {
        /** Upper bound on one backfill pass; a user dictionary never approaches this size. */
        const val BACKFILL_LIMIT = 5_000
    }
}
