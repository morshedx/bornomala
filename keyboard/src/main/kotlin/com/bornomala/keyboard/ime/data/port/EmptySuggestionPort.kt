package com.bornomala.keyboard.ime.data.port

import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.Suggestion
import com.bornomala.keyboard.ime.domain.port.SuggestionPort
import javax.inject.Inject

/**
 * Safe fallback [SuggestionPort] that returns no candidates and ignores learning. The app
 * binds the real adapter over the :suggestions module; this keeps the keyboard usable
 * (just without dictionary suggestions) when that module is not yet wired.
 */
class EmptySuggestionPort @Inject constructor() : SuggestionPort {
    override suspend fun query(
        language: KeyboardLanguage,
        currentWord: String,
        previousWord: String,
        limit: Int,
    ): List<Suggestion> = emptyList()

    override fun recordCommitted(language: KeyboardLanguage, word: String) = Unit
}
