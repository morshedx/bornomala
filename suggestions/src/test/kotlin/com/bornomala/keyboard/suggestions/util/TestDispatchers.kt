package com.bornomala.keyboard.suggestions.util

import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * A [DispatcherProvider] that routes every role to a single test dispatcher, so
 * suspend logic under test runs deterministically on the test scheduler.
 */
class TestDispatcherProvider(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val default: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
    override val mainImmediate: CoroutineDispatcher = dispatcher
}
