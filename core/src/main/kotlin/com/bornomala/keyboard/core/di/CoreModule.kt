package com.bornomala.keyboard.core.di

import com.bornomala.keyboard.core.dispatchers.DefaultDispatcherProvider
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for cross-cutting core abstractions. Installed in the singleton
 * component so every feature module resolves the same [DispatcherProvider].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(
        impl: DefaultDispatcherProvider,
    ): DispatcherProvider
}
