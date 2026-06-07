package com.bornomala.keyboard.emoji.util

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/**
 * [DispatcherProvider] that routes every dispatcher to a single test dispatcher so
 * coroutine work runs deterministically on the test scheduler.
 */
class TestDispatcherProvider(
    private val dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val default: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
    override val mainImmediate: CoroutineDispatcher = dispatcher
}
