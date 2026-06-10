package com.bornomala.keyboard.ime.domain.model

import androidx.compose.runtime.Immutable

/**
 * The semantic action a key performs when activated. Modelling actions as a sealed type
 * (rather than raw key codes) keeps the layout tables declarative and lets the
 * interactor handle each case exhaustively.
 *
 * All variants are immutable value types so they can live inside `@Immutable` keyboard
 * state without triggering recomposition churn, and so that pre-built layout tables can
 * be shared as constants across keystrokes (zero per-key allocation in the hot path).
 */
@Immutable
sealed interface KeyAction {

    /** Emit a literal character (letters, digits, punctuation, symbols). */
    @Immutable
    data class Character(val char: Char) : KeyAction

    /** Emit an arbitrary string in one commit (used for a few multi-char symbols). */
    @Immutable
    data class Text(val text: String) : KeyAction

    /** Toggle shift / caps lock. */
    @Immutable
    data object Shift : KeyAction

    /** Delete backwards (one char, or selection). Repeats while held. */
    @Immutable
    data object Backspace : KeyAction

    /** Space. Double-space within the timeout inserts ". ". */
    @Immutable
    data object Space : KeyAction

    /** Newline / editor action (search, send, go, etc., based on the field). */
    @Immutable
    data object Enter : KeyAction

    /** Switch to the next language in the cycle. */
    @Immutable
    data object SwitchLanguage : KeyAction

    /** Jump to the symbols page (or back to alpha when already on a symbol page). */
    @Immutable
    data object ToSymbols : KeyAction

    /** Return to the alphabetic page from a symbol page. */
    @Immutable
    data object ToAlpha : KeyAction

    /** Toggle between the two symbol pages. */
    @Immutable
    data object ToggleSymbolsPage : KeyAction

    /** Open the emoji panel (handled by the host service / :emoji module). */
    @Immutable
    data object Emoji : KeyAction

    /** Show the system input-method picker (switch to another keyboard app). */
    @Immutable
    data object ShowImePicker : KeyAction

    /** No-op placeholder used for spacer cells that keep rows aligned. */
    @Immutable
    data object None : KeyAction
}
