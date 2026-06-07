package com.bornomala.keyboard.emoji.di

import android.content.Context
import androidx.room.Room
import com.bornomala.keyboard.emoji.data.EmojiRepositoryImpl
import com.bornomala.keyboard.emoji.data.local.EmojiDatabase
import com.bornomala.keyboard.emoji.data.local.EmojiUsageDao
import com.bornomala.keyboard.emoji.domain.repository.EmojiRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the :emoji module.
 *
 * The Room database is created lazily by Hilt the first time the DAO is actually
 * injected (i.e. when the emoji panel is first used), keeping it off the IME
 * cold-start path. [EmojiRepository] is bound to its default implementation so the
 * presentation layer depends only on the domain interface.
 */
@Module
@InstallIn(SingletonComponent::class)
object EmojiDatabaseModule {

    @Provides
    @Singleton
    fun provideEmojiDatabase(
        @ApplicationContext context: Context,
    ): EmojiDatabase = Room.databaseBuilder(
        context,
        EmojiDatabase::class.java,
        EmojiDatabase.DATABASE_NAME,
    )
        // Usage history is derived, non-critical data; on an incompatible schema change
        // it is safe and cheap to rebuild rather than ship a migration.
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideEmojiUsageDao(database: EmojiDatabase): EmojiUsageDao =
        database.emojiUsageDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EmojiRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindEmojiRepository(impl: EmojiRepositoryImpl): EmojiRepository
}
