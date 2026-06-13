package com.bornomala.keyboard.clipboard.data.repository

import com.bornomala.keyboard.clipboard.data.local.ClipboardDao
import com.bornomala.keyboard.clipboard.data.local.ClipboardEntity
import com.bornomala.keyboard.clipboard.data.local.toDomain
import com.bornomala.keyboard.clipboard.domain.model.ClipboardItem
import com.bornomala.keyboard.clipboard.domain.repository.ClipboardRepository
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.core.result.AppError
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.core.result.appRunCatching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [ClipboardRepository].
 *
 * All disk work runs on [DispatcherProvider.io] so the IME input path and UI never block.
 * Writes are wrapped in [appRunCatching] to convert Room exceptions into typed
 * [AppError.Storage]. Eviction is delegated to the DAO's atomic `addAndEvict`.
 */
@Singleton
class DefaultClipboardRepository @Inject constructor(
    private val dao: ClipboardDao,
    private val dispatchers: DispatcherProvider,
) : ClipboardRepository {

    override fun observeHistory(): Flow<List<ClipboardItem>> =
        dao.observeAll()
            .map { it.toDomain() }
            .flowOn(dispatchers.io)

    override fun searchHistory(query: String): Flow<List<ClipboardItem>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            observeHistory()
        } else {
            dao.search(buildLikePattern(trimmed))
                .map { it.toDomain() }
                .flowOn(dispatchers.io)
        }
    }

    override suspend fun addItem(text: String): AppResult<Long> {
        val normalized = text.trim()
        if (normalized.isEmpty()) {
            return AppResult.failure(AppError.Validation("Cannot store blank clipboard text"))
        }
        return withContext(dispatchers.io) {
            appRunCatching {
                dao.addAndEvict(
                    entity = ClipboardEntity(
                        text = normalized,
                        pinned = false,
                        createdAt = System.currentTimeMillis(),
                    ),
                    maxUnpinned = ClipboardRepository.MAX_HISTORY_ITEMS,
                )
            }
        }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Unit> =
        withContext(dispatchers.io) {
            appRunCatching {
                if (dao.findById(id) == null) {
                    throw NoSuchElementException("Clipboard item $id not found")
                }
                dao.updatePinned(id, pinned)
                // Re-enforce the cap: unpinning may push the non-pinned count over budget.
                if (!pinned) {
                    dao.evictOverflow(ClipboardRepository.MAX_HISTORY_ITEMS)
                }
            }.mapNotFound(id)
        }

    override suspend fun deleteItem(id: Long): AppResult<Unit> =
        withContext(dispatchers.io) {
            appRunCatching { dao.deleteById(id) }
        }

    override suspend fun clearUnpinned(): AppResult<Unit> =
        withContext(dispatchers.io) {
            appRunCatching { dao.deleteAllUnpinned() }
        }

    override suspend fun exportAll(): AppResult<List<ClipboardItem>> =
        withContext(dispatchers.io) {
            appRunCatching { dao.getAllOnce().toDomain() }
        }

    override suspend fun replaceAll(items: List<ClipboardItem>): AppResult<Unit> =
        withContext(dispatchers.io) {
            appRunCatching {
                dao.replaceAll(
                    items.map { item ->
                        ClipboardEntity(
                            id = item.id,
                            text = item.text,
                            pinned = item.pinned,
                            createdAt = item.createdAt,
                        )
                    },
                )
            }
        }

    /**
     * Builds a case-insensitive `LIKE` pattern, escaping the SQL wildcards `%`, `_` and
     * the escape char `\` so user-typed queries are treated as literal substrings.
     */
    private fun buildLikePattern(query: String): String {
        val escaped = buildString(query.length + 2) {
            for (c in query) {
                when (c) {
                    '\\', '%', '_' -> append('\\').append(c)
                    else -> append(c)
                }
            }
        }
        return "%$escaped%"
    }

    /**
     * Normalizes a [NoSuchElementException] (item missing) into [AppError.NotFound] while
     * leaving other failures as-is.
     */
    private fun AppResult<Unit>.mapNotFound(id: Long): AppResult<Unit> = when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> if (error.cause is NoSuchElementException) {
            AppResult.failure(AppError.NotFound("Clipboard item $id not found", error.cause))
        } else {
            this
        }
    }
}
