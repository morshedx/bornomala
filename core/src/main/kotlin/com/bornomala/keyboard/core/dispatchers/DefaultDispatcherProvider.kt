package com.bornomala.keyboard.core.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [DispatcherProvider] backed by the standard [Dispatchers].
 * Bound in [com.bornomala.keyboard.core.di.CoreModule].
 */
@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
}
