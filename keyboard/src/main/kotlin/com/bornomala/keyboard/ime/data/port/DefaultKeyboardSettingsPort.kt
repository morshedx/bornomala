package com.bornomala.keyboard.ime.data.port

import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.port.KeyboardSettings
import com.bornomala.keyboard.ime.domain.port.KeyboardSettingsPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe fallback [KeyboardSettingsPort] that holds settings in memory with defaults. The
 * app binds the real DataStore-backed adapter from the :settings module; this fallback
 * keeps the keyboard fully functional in isolation and remembers the last language for the
 * lifetime of the process.
 */
@Singleton
class DefaultKeyboardSettingsPort @Inject constructor() : KeyboardSettingsPort {

    private val state = MutableStateFlow(KeyboardSettings())

    override val settings: Flow<KeyboardSettings> = state.asStateFlow()

    override suspend fun setLastLanguage(language: KeyboardLanguage) {
        state.update { it.copy(lastLanguage = language) }
    }
}
