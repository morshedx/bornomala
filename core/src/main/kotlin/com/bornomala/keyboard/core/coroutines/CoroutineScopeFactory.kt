package com.bornomala.keyboard.core.coroutines

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Creates short-lived, owner-bound [CoroutineScope]s (e.g. for the IME service
 * lifecycle). The caller is responsible for cancelling the returned scope when its
 * owner is destroyed.
 *
 * @param dispatchers source of dispatchers; the returned scope defaults to [DispatcherProvider.mainImmediate]
 *   so UI-affecting coroutines launch without an extra dispatch hop.
 * @param onError invoked for uncaught child failures.
 */
object CoroutineScopeFactory {

    fun create(
        dispatchers: DispatcherProvider,
        onError: (Throwable) -> Unit = {},
    ): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, throwable -> onError(throwable) }
        return CoroutineScope(SupervisorJob() + dispatchers.mainImmediate + handler)
    }
}
