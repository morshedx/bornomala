package com.bornomala.keyboard.ime.domain.input

/**
 * Behavioural toggles the [InputInteractor] reads on the hot path. A small immutable value
 * object snapshotted from user settings so the interactor never touches DataStore directly
 * and tests can vary behaviour trivially.
 *
 * @param autoCapitalization capitalize the first letter of a sentence in English.
 * @param doubleSpacePeriod convert a quick double-space into ". ".
 * @param banglaTransliteration route Bangla character input through the engine.
 * @param suggestionsEnabled compute and show suggestions.
 * @param doubleSpaceWindowMs max gap between the two spaces for the period shortcut.
 */
data class InputConfig(
    val autoCapitalization: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val banglaTransliteration: Boolean = true,
    val suggestionsEnabled: Boolean = true,
    val doubleSpaceWindowMs: Long = 300L,
)
