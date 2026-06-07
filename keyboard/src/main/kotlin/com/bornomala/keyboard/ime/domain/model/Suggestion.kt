package com.bornomala.keyboard.ime.domain.model

import androidx.compose.runtime.Immutable

/**
 * A single candidate shown in the suggestion bar. This is the keyboard module's own
 * lightweight view of a suggestion; the richer ranking data produced by the :suggestions
 * module is mapped down to this at the port boundary so the renderer stays decoupled.
 *
 * @param text the word/phrase that would be committed when tapped.
 * @param isAutoCorrect when true the candidate is the auto-correction target and is
 *   emphasised (and committed on space if auto-correct is enabled).
 * @param isTransliteration when true this came from the Bangla transliteration engine
 *   (the raw current-word candidate) rather than the dictionary.
 */
@Immutable
data class Suggestion(
    val text: String,
    val isAutoCorrect: Boolean = false,
    val isTransliteration: Boolean = false,
)
