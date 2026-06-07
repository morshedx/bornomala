package com.bornomala.keyboard.clipboard

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Test [DispatcherProvider] that routes every dispatcher to a single test dispatcher so
 * coroutine work runs deterministically under `runTest`.
 */
class TestDispatcherProvider(
    private val dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val default: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
    override val mainImmediate: CoroutineDispatcher = dispatcher
}
