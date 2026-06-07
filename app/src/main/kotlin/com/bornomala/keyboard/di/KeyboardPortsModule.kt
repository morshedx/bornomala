package com.bornomala.keyboard.di

import com.bornomala.keyboard.glue.KeyboardSettingsPortAdapter
import com.bornomala.keyboard.glue.SuggestionPortAdapter
import com.bornomala.keyboard.glue.TransliterationPortAdapter
import com.bornomala.keyboard.ime.domain.port.KeyboardSettingsPort
import com.bornomala.keyboard.ime.domain.port.SuggestionPort
import com.bornomala.keyboard.ime.domain.port.TransliterationPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-level wiring that fulfils the keyboard's inbound ports with adapters over the real
 * feature engines. This is the single seam where the otherwise-decoupled `:keyboard` module
 * is connected to `:transliteration`, `:suggestions`, and `:settings`.
 *
 * Each feature module binds its own engine/repository in its own Hilt module; here we only
 * bind the thin port adapters, so swapping in a different provider later (e.g. a cloud
 * suggestion source) is a one-line change with no keyboard refactor.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class KeyboardPortsModule {

    @Binds
    @Singleton
    abstract fun bindTransliterationPort(impl: TransliterationPortAdapter): TransliterationPort

    @Binds
    @Singleton
    abstract fun bindSuggestionPort(impl: SuggestionPortAdapter): SuggestionPort

    @Binds
    @Singleton
    abstract fun bindKeyboardSettingsPort(impl: KeyboardSettingsPortAdapter): KeyboardSettingsPort
}
