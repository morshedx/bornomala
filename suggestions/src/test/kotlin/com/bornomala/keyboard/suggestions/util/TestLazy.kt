package com.bornomala.keyboard.suggestions.util

import dagger.Lazy

/** Wraps an already-constructed value in a [dagger.Lazy] for tests. */
fun <T> lazyOf(value: T): Lazy<T> = Lazy { value }
