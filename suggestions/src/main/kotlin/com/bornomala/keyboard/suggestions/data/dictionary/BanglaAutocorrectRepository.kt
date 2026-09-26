package com.bornomala.keyboard.suggestions.data.dictionary

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.suggestions.domain.AvroRenderer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily builds the [BanglaAutocorrectTable] on first lookup, off the main thread, and caches
 * it for the process lifetime. A failed load degrades to [BanglaAutocorrectTable.EMPTY] so a
 * broken asset only costs the feature, never typing.
 */
@Singleton
class BanglaAutocorrectRepository @Inject constructor(
    private val source: DictionarySource,
    private val renderer: AvroRenderer,
    private val dispatchers: DispatcherProvider,
) {

    @Volatile
    private var cached: BanglaAutocorrectTable? = null
    private val mutex = Mutex()

    /** The Bangla word for the exact roman spelling [roman], or null. */
    suspend fun lookup(roman: String): String? = table().lookup(roman)

    private suspend fun table(): BanglaAutocorrectTable {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            val built = withContext(dispatchers.default) {
                runCatching {
                    BanglaAutocorrectTable.build(
                        avroJsonLines = source.autocorrectLines(),
                        overlayLines = source.loanwordLines(),
                        render = renderer::render,
                    )
                }.getOrDefault(BanglaAutocorrectTable.EMPTY)
            }
            cached = built
            built
        }
    }
}
