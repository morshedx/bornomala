package com.bornomala.keyboard.ime.domain.model

import androidx.compose.runtime.Immutable

/**
 * The complete, immutable UI/input state of the keyboard at a moment in time. Held in a
 * [kotlinx.coroutines.flow.StateFlow] by the state holder and rendered directly by Compose.
 *
 * Being a single immutable data class makes recomposition cheap and predictable: Compose
 * compares the old/new instances structurally and only the changed sub-tree recomposes.
 * The list fields are wrapped via [Immutable] payload types so Compose treats them as
 * stable.
 *
 * @param language active input language.
 * @param page active page (alpha / symbols / extra symbols).
 * @param shift tri-state shift.
 * @param suggestions current suggestion-bar candidates.
 * @param composingText the in-progress word being transliterated / corrected (shown
 *   underlined in the field via the InputConnection composing region); empty when none.
 * @param showNumberRow whether the dedicated number row is shown above the top letter row.
 * @param suggestionsEnabled whether the suggestion bar is active (user setting).
 * @param enterIsAccent whether the Enter key is styled as the accent action (send/search).
 * @param hasText whether the edited field currently holds any text. Drives the top strip:
 *   empty field -> show the quick-action tools; once typing begins -> show suggestions.
 */
@Immutable
data class KeyboardState(
    val language: KeyboardLanguage = KeyboardLanguage.ENGLISH,
    val page: KeyboardPage = KeyboardPage.ALPHA,
    val shift: ShiftState = ShiftState.OFF,
    val suggestions: List<Suggestion> = emptyList(),
    val composingText: String = "",
    val showNumberRow: Boolean = false,
    val suggestionsEnabled: Boolean = true,
    val enterIsAccent: Boolean = false,
    val panel: KeyboardPanel = KeyboardPanel.NONE,
    val isEmailField: Boolean = false,
    val panelQuery: String = "",
    val panelSearchActive: Boolean = false,
    val hasText: Boolean = false,
) {
    /** True while a word is being composed (Bangla transliteration or English correction). */
    val isComposing: Boolean
        get() = composingText.isNotEmpty()
}
