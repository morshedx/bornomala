package com.bornomala.keyboard.emoji.data

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.core.result.appRunCatching
import com.bornomala.keyboard.emoji.data.catalog.EmojiCatalog
import com.bornomala.keyboard.emoji.data.catalog.EmojiSearchIndex
import com.bornomala.keyboard.emoji.data.local.EmojiUsageDao
import com.bornomala.keyboard.emoji.domain.model.Emoji
import com.bornomala.keyboard.emoji.domain.model.EmojiCategory
import com.bornomala.keyboard.emoji.domain.repository.EmojiRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Default [EmojiRepository] backed by the bundled [EmojiCatalog] and the Room-persisted
 * [EmojiUsageDao].
 *
 * Performance notes:
 * - The catalog and search index are materialized lazily on first access ([byGlyph],
 *   [searchIndex]) so the IME cold-start path pays nothing until the user opens the
 *   emoji panel.
 * - Category and search reads run on [DispatcherProvider.default] (CPU-bound); usage
 *   reads/writes run on [DispatcherProvider.io] (disk). Nothing blocks the Main thread.
 * - Usage-history flows map glyph rows back to full [Emoji] objects via the in-memory
 *   [byGlyph] map, so no extra DB joins or per-emit catalog scans occur.
 */
@Singleton
class EmojiRepositoryImpl @Inject constructor(
    private val usageDao: EmojiUsageDao,
    private val dispatchers: DispatcherProvider,
) : EmojiRepository {

    /** Glyph → catalog entry, for resolving persisted usage rows back to emoji. */
    private val byGlyph: Map<String, Emoji> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EmojiCatalog.all.associateBy { it.glyph }
    }

    private val searchIndex: EmojiSearchIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EmojiSearchIndex(EmojiCatalog.all)
    }

    override suspend fun emojisFor(category: EmojiCategory): AppResult<List<Emoji>> =
        withContext(dispatchers.default) {
            appRunCatching {
                if (category.isDynamic) {
                    emptyList()
                } else {
                    EmojiCatalog.byCategory[category].orEmpty()
                }
            }
        }

    override suspend fun search(query: String): AppResult<List<Emoji>> =
        withContext(dispatchers.default) {
            appRunCatching { searchIndex.search(query) }
        }

    override fun observeRecent(limit: Int): Flow<List<Emoji>> =
        usageDao.observeRecent(limit)
            .map { rows -> rows.mapNotNull { byGlyph[it.emoji] } }
            .flowOn(dispatchers.io)

    override fun observeFrequent(limit: Int): Flow<List<Emoji>> =
        usageDao.observeFrequent(limit)
            .map { rows -> rows.mapNotNull { byGlyph[it.emoji] } }
            .flowOn(dispatchers.io)

    override suspend fun recordUsage(emoji: Emoji): AppResult<Unit> =
        withContext(dispatchers.io) {
            appRunCatching {
                usageDao.upsertUsage(emoji.glyph, System.currentTimeMillis())
            }
        }

    override suspend fun clearUsage(): AppResult<Unit> =
        withContext(dispatchers.io) {
            appRunCatching { usageDao.clear() }
        }
}
