package com.bornomala.keyboard.suggestions.di

import android.content.Context
import com.bornomala.keyboard.suggestions.data.DefaultSuggestionEngine
import com.bornomala.keyboard.suggestions.data.dictionary.AssetDictionarySource
import com.bornomala.keyboard.suggestions.data.dictionary.DictionarySource
import com.bornomala.keyboard.suggestions.data.local.SuggestionsDatabase
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryDao
import com.bornomala.keyboard.suggestions.data.provider.OfflineProvider
import com.bornomala.keyboard.suggestions.domain.SuggestionEngine
import com.bornomala.keyboard.suggestions.domain.SuggestionProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Hilt graph for the :suggestions module.
 *
 * Wiring decisions:
 * - The active provider set ([SuggestionProvider]) is populated via `@IntoSet` and
 *   contains ONLY [OfflineProvider] in V1. [com.bornomala.keyboard.suggestions.data.provider.FutureCloudProvider]
 *   is deliberately absent, so no network-capable provider exists in the graph —
 *   the privacy contract is enforced at the DI boundary, not just at runtime.
 * - [DefaultSuggestionEngine] receives that set and is exposed behind the
 *   [SuggestionEngine] domain interface.
 * - The Room DAO is provided so [dagger.Lazy]<UserDictionaryDao> defers database
 *   creation until first real use (cold-start protection).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SuggestionsModule {

    @Binds
    @Singleton
    abstract fun bindSuggestionEngine(impl: DefaultSuggestionEngine): SuggestionEngine

    @Binds
    @Singleton
    abstract fun bindDictionarySource(impl: AssetDictionarySource): DictionarySource

    /**
     * Registers the offline provider into the engine's provider set. This is the ONLY
     * provider bound in V1. To enable cloud suggestions later, add a parallel
     * `@Binds @IntoSet` for the cloud provider (and INTERNET permission) — a single,
     * explicit, reviewable change.
     */
    @Binds
    @Singleton
    @IntoSet
    abstract fun bindOfflineProvider(impl: OfflineProvider): SuggestionProvider

    companion object {

        @Provides
        @Singleton
        fun provideSuggestionsDatabase(
            @ApplicationContext context: Context,
        ): SuggestionsDatabase = SuggestionsDatabase.build(context)

        @Provides
        @Singleton
        fun provideUserDictionaryDao(
            database: SuggestionsDatabase,
        ): UserDictionaryDao = database.userDictionaryDao()
    }
}
