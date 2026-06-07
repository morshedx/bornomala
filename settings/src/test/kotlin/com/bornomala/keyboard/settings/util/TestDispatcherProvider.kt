package com.bornomala.keyboard.settings.util

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Routes every dispatcher to a single test dispatcher so coroutine work runs on the test
 * scheduler, keeping repository tests deterministic.
 */
class TestDispatcherProvider(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val default: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
    override val mainImmediate: CoroutineDispatcher = dispatcher
}
