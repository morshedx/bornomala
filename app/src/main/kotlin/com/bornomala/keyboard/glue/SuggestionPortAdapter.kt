package com.bornomala.keyboard.glue

import com.bornomala.keyboard.core.coroutines.AppCoroutineScope
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.port.SuggestionPort
import com.bornomala.keyboard.suggestions.domain.SuggestionEngine
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.domain.model.SuggestionRequest
import kotlinx.coroutines.launch
import com.bornomala.keyboard.ime.domain.model.Suggestion as KeyboardSuggestion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts the :suggestions module's [SuggestionEngine] onto the keyboard's [SuggestionPort].
 *
 * [query] is `suspend` and is invoked by the IME off the main thread; it maps the request
 * languages/types across the module boundary and projects the rich domain [com.bornomala.keyboard.suggestions.domain.model.Suggestion]
 * down to the keyboard's lightweight [KeyboardSuggestion]. [recordCommitted] is
 * fire-and-forget: it launches learning on a process scope so input is never blocked, and
 * never on the main thread.
 */
@Singleton
class SuggestionPortAdapter @Inject constructor(
    private val engine: SuggestionEngine,
    dispatchers: DispatcherProvider,
) : SuggestionPort {

    private val learningScope = AppCoroutineScope(dispatchers)

    /** Last two committed words per language, so learning records bigram + trigram contexts. */
    private val lastCommitted = HashMap<SuggestionLanguage, String>()
    private val secondLastCommitted = HashMap<SuggestionLanguage, String>()

    override suspend fun query(
        language: KeyboardLanguage,
        currentWord: String,
        previousWord: String,
        secondPreviousWord: String,
        limit: Int,
    ): List<KeyboardSuggestion> {
        val request = SuggestionRequest(
            currentWord = currentWord,
            previousWord = previousWord,
            secondPreviousWord = secondPreviousWord,
            language = language.toSuggestionLanguage(),
            limit = limit,
        )
        val ranked = engine.getSuggestions(request)
        if (ranked.isEmpty()) return emptyList()
        // The top candidate is highlighted as the auto-correct target when it is not just a
        // verbatim echo of what the user typed.
        return ranked.mapIndexed { index, s ->
            KeyboardSuggestion(
                text = s.word,
                isAutoCorrect = index == 0 && !s.isExactMatch && currentWord.isNotEmpty(),
            )
        }
    }

    override fun recordCommitted(language: KeyboardLanguage, word: String) {
        if (word.isBlank()) return
        val lang = language.toSuggestionLanguage()
        val previous = lastCommitted[lang].orEmpty()
        val secondPrevious = secondLastCommitted[lang].orEmpty()
        secondLastCommitted[lang] = previous
        lastCommitted[lang] = word
        learningScope.launch {
            engine.onWordCommitted(
                word = word,
                previousWord = previous,
                secondPreviousWord = secondPrevious,
                language = lang,
            )
        }
    }

    private fun KeyboardLanguage.toSuggestionLanguage(): SuggestionLanguage = when (this) {
        KeyboardLanguage.ENGLISH -> SuggestionLanguage.ENGLISH
        KeyboardLanguage.BANGLA -> SuggestionLanguage.BANGLA
    }
}
