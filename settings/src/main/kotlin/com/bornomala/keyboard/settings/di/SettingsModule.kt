package com.bornomala.keyboard.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.settings.data.DataStoreSettingsRepository
import com.bornomala.keyboard.settings.domain.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Hilt bindings for the settings module.
 *
 * The Preferences [DataStore] is a process singleton (only one instance per file may
 * exist) and is constructed lazily by the factory — no disk I/O happens at injection
 * time, keeping IME cold start cheap. Persistence runs on the injected IO dispatcher.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    private const val DATASTORE_FILE_NAME = "bornomala_settings"

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): DataStore<Preferences> {
        // Process-lifetime supervisor scope on the IO dispatcher for DataStore's internal
        // read/write work, so persistence never touches the main thread and a single
        // failing edit cannot cancel subsequent ones.
        val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
        return PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile(DATASTORE_FILE_NAME)
        }
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: DataStore<Preferences>,
        dispatchers: DispatcherProvider,
    ): SettingsRepository = DataStoreSettingsRepository(dataStore, dispatchers)
}
