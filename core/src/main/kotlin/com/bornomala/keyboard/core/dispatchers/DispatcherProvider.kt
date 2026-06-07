package com.bornomala.keyboard.core.dispatchers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Abstraction over [CoroutineDispatcher]s so dispatchers can be injected and swapped
 * (e.g. with `StandardTestDispatcher`) in tests, keeping suspend logic free of
 * hardcoded `Dispatchers.*` references.
 */
interface DispatcherProvider {
    /** CPU-bound work: transliteration, ranking, parsing. */
    val default: CoroutineDispatcher

    /** I/O-bound work: Room, DataStore, file reads. */
    val io: CoroutineDispatcher

    /** UI/main thread. Never run blocking work here. */
    val main: CoroutineDispatcher

    /** Main thread without dispatch when already on it; for immediate UI updates. */
    val mainImmediate: CoroutineDispatcher
}
