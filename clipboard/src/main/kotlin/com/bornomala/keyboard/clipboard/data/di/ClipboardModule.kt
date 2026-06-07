package com.bornomala.keyboard.clipboard.data.di

import android.content.Context
import androidx.room.Room
import com.bornomala.keyboard.clipboard.data.local.ClipboardDao
import com.bornomala.keyboard.clipboard.data.local.ClipboardDatabase
import com.bornomala.keyboard.clipboard.data.repository.DefaultClipboardRepository
import com.bornomala.keyboard.clipboard.domain.repository.ClipboardRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the clipboard module.
 *
 * The database is provided as a [Singleton]. Room opens the file lazily on first query,
 * not at construction, so injecting this does not touch disk during IME cold start.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClipboardDatabaseModule {

    @Provides
    @Singleton
    fun provideClipboardDatabase(
        @ApplicationContext context: Context,
    ): ClipboardDatabase = Room.databaseBuilder(
        context,
        ClipboardDatabase::class.java,
        ClipboardDatabase.DATABASE_NAME,
    ).build()

    @Provides
    fun provideClipboardDao(database: ClipboardDatabase): ClipboardDao =
        database.clipboardDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClipboardBindingModule {

    @Binds
    @Singleton
    abstract fun bindClipboardRepository(
        impl: DefaultClipboardRepository,
    ): ClipboardRepository
}
