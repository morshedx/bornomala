package com.bornomala.keyboard.core.coroutines

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Process-wide application scope for fire-and-forget work that must outlive a single
 * screen but should be cancellable on process death (e.g. background dictionary
 * learning, clipboard pruning).
 *
 * Uses a [SupervisorJob] so a failure in one child does not cancel siblings, and a
 * [CoroutineExceptionHandler] so uncaught failures never crash the IME process.
 * Defaults to the [DispatcherProvider.default] dispatcher; callers should switch to
 * [DispatcherProvider.io] for I/O.
 */
class AppCoroutineScope(
    dispatchers: DispatcherProvider,
    onError: (Throwable) -> Unit = {},
) : CoroutineScope {

    private val handler = CoroutineExceptionHandler { _, throwable ->
        onError(throwable)
    }

    override val coroutineContext: CoroutineContext =
        SupervisorJob() + dispatchers.default + handler
}
