package com.bornomala.keyboard.ime.presentation

import androidx.compose.runtime.Immutable
import com.bornomala.keyboard.ime.domain.model.KeyAction

/**
 * Stable callback bundle handed to the keyboard composables. Wrapping the lambdas in an
 * `@Immutable` holder (and creating it once with `remember`) keeps them stable across
 * recompositions, so key composables are not invalidated every frame — important for the
 * sub-16ms key path and to avoid recomposition storms.
 *
 * @param onKey invoked when a key's primary action fires.
 * @param onLongPressChar invoked when a character is chosen from a long-press popup.
 * @param onSuggestion invoked when a suggestion chip is tapped.
 * @param onOpenSettings invoked to open the full keyboard settings app (long-press comma).
 * @param onToggleSettingsMenu toggles the in-keyboard settings menu panel (gear button).
 * @param onOpenSettingsSection opens the full settings app at the given section key.
 * @param onToggleEmoji toggles the emoji picker panel.
 * @param onToggleNumbers toggles between the numeric pad and the alphabetic keyboard.
 * @param onToggleClipboard toggles the clipboard history panel.
 * @param onPaste commits a clipboard item's text into the field.
 * @param onEmoji commits a chosen emoji glyph (panel stays open).
 * @param onHideKeyboard dismisses the keyboard (toolbar down-chevron).
 */
@Immutable
class KeyboardCallbacks(
    val onKey: (KeyAction) -> Unit,
    val onLongPressChar: (Char) -> Unit,
    val onSuggestion: (String) -> Unit,
    val onOpenSettings: () -> Unit,
    val onToggleSettingsMenu: () -> Unit,
    val onOpenSettingsSection: (String) -> Unit,
    val onToggleEmoji: () -> Unit,
    val onToggleNumbers: () -> Unit,
    val onToggleClipboard: () -> Unit,
    val onPaste: (String) -> Unit,
    val onEmoji: (String) -> Unit,
    val onHideKeyboard: () -> Unit,
    /** Routes a key press to the in-panel search query (emoji/clipboard search keyboard). */
    val onSearchKey: (KeyAction) -> Unit,
    /** Activates the in-panel search keyboard. */
    val onOpenSearch: () -> Unit,
    /** Closes the in-panel search keyboard and clears the query. */
    val onCloseSearch: () -> Unit,
)
