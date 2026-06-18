package com.bornomala.keyboard.ime.domain.state

import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.KeyboardPage
import com.bornomala.keyboard.ime.domain.model.KeyboardPanel
import com.bornomala.keyboard.ime.domain.model.KeyboardState
import com.bornomala.keyboard.ime.domain.model.ShiftState
import com.bornomala.keyboard.ime.domain.model.Suggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for [KeyboardState], exposed as a [StateFlow] the Compose
 * renderer collects. All mutations go through the small, intention-revealing methods here
 * so transitions are centralised and unit-testable without any Android dependency.
 *
 * Each method uses [MutableStateFlow.update] which only emits when the resulting state is
 * structurally different, so no-op updates do not trigger recomposition. The state object
 * itself is immutable, keeping Compose comparisons cheap.
 *
 * This class is deliberately framework-free (no InputConnection, no dispatchers); the
 * service/interactor drives it. That is what lets the keyboard state machine be covered by
 * fast JVM tests.
 */
class KeyboardStateHolder(
    initial: KeyboardState = KeyboardState(),
) {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<KeyboardState> = _state.asStateFlow()

    /** Current snapshot, for synchronous reads on the input hot path. */
    val current: KeyboardState
        get() = _state.value

    /** Switches to [language], resetting to the alpha page and clearing composing text. */
    fun setLanguage(language: KeyboardLanguage) = _state.update {
        if (it.language == language) it
        else it.copy(
            language = language,
            page = KeyboardPage.ALPHA,
            composingText = "",
            suggestions = emptyList(),
        )
    }

    /** Cycles to the next language (English <-> Bangla). */
    fun cycleLanguage() = setLanguage(current.language.next())

    /** Applies a single shift-key tap (OFF -> SHIFTED -> CAPS_LOCK -> OFF). */
    fun toggleShift() = _state.update { it.copy(shift = it.shift.toggled()) }

    /** Forces a specific shift state (used by auto-capitalization). */
    fun setShift(shift: ShiftState) = _state.update {
        if (it.shift == shift) it else it.copy(shift = shift)
    }

    /** Reverts SHIFTED to OFF after a character is committed; CAPS_LOCK/OFF unchanged. */
    fun consumeShiftAfterChar() = _state.update {
        val next = it.shift.afterCharCommit()
        if (next == it.shift) it else it.copy(shift = next)
    }

    /** Moves to the symbols page (page one). */
    fun showSymbols() = _state.update {
        it.copy(page = KeyboardPage.SYMBOLS, composingText = "", suggestions = emptyList(), panel = KeyboardPanel.NONE, panelSearchActive = false, panelQuery = "")
    }

    /** Returns to the alphabetic page for the active language (and closes any panel). */
    fun showAlpha() = _state.update {
        if (it.page == KeyboardPage.ALPHA && it.panel == KeyboardPanel.NONE) it
        else it.copy(page = KeyboardPage.ALPHA, panel = KeyboardPanel.NONE, panelSearchActive = false, panelQuery = "")
    }

    /** Opens the calculator-style numeric pad. */
    fun showNumpad() = _state.update {
        if (it.page == KeyboardPage.NUMPAD) it
        else it.copy(page = KeyboardPage.NUMPAD, composingText = "", suggestions = emptyList(), panel = KeyboardPanel.NONE, panelSearchActive = false, panelQuery = "")
    }

    /** Toggles the clipboard panel overlay (tab-like: closes any other panel). */
    fun toggleClipboard() = _state.update {
        val next = if (it.panel == KeyboardPanel.CLIPBOARD) KeyboardPanel.NONE else KeyboardPanel.CLIPBOARD
        it.copy(panel = next, page = KeyboardPage.ALPHA, panelSearchActive = false, panelQuery = "")
    }

    /** Toggles the emoji panel overlay (tab-like: closes any other panel). */
    fun toggleEmoji() = _state.update {
        val next = if (it.panel == KeyboardPanel.EMOJI) KeyboardPanel.NONE else KeyboardPanel.EMOJI
        it.copy(panel = next, page = KeyboardPage.ALPHA, panelSearchActive = false, panelQuery = "")
    }

    /** Toggles the in-keyboard settings menu overlay (tab-like: closes any other panel). */
    fun toggleSettingsMenu() = _state.update {
        val next = if (it.panel == KeyboardPanel.SETTINGS) KeyboardPanel.NONE else KeyboardPanel.SETTINGS
        it.copy(panel = next, page = KeyboardPage.ALPHA, panelSearchActive = false, panelQuery = "")
    }

    /** Closes any open panel, returning to the key grid. */
    fun hidePanel() = _state.update {
        if (it.panel == KeyboardPanel.NONE) it
        else it.copy(panel = KeyboardPanel.NONE, panelSearchActive = false, panelQuery = "")
    }

    /** Activates/deactivates the in-panel search keyboard; deactivating clears the query. */
    fun setPanelSearch(active: Boolean) = _state.update {
        if (it.panelSearchActive == active) it
        else it.copy(panelSearchActive = active, panelQuery = if (active) it.panelQuery else "")
    }

    /** Appends typed text to the in-panel search query. */
    fun appendPanelQuery(text: String) = _state.update { it.copy(panelQuery = it.panelQuery + text) }

    /** Removes the last character from the in-panel search query. */
    fun backspacePanelQuery() = _state.update {
        if (it.panelQuery.isEmpty()) it else it.copy(panelQuery = it.panelQuery.dropLast(1))
    }

    /** Toggles between the two symbol pages; only meaningful while on a symbol page. */
    fun toggleSymbolsPage() = _state.update {
        when (it.page) {
            KeyboardPage.SYMBOLS -> it.copy(page = KeyboardPage.SYMBOLS_EXTRA)
            KeyboardPage.SYMBOLS_EXTRA -> it.copy(page = KeyboardPage.SYMBOLS)
            KeyboardPage.ALPHA -> it
            KeyboardPage.NUMPAD -> it
        }
    }

    /** Replaces the suggestion list shown in the bar. */
    fun setSuggestions(suggestions: List<Suggestion>) = _state.update {
        if (it.suggestions == suggestions) it else it.copy(suggestions = suggestions)
    }

    /** Updates the composing (in-progress) word string. */
    fun setComposing(text: String) = _state.update {
        if (it.composingText == text) it else it.copy(composingText = text)
    }

    /** Clears composing text and suggestions (e.g. on word commit / cursor jump). */
    fun clearComposingAndSuggestions() = _state.update {
        if (it.composingText.isEmpty() && it.suggestions.isEmpty()) it
        else it.copy(composingText = "", suggestions = emptyList())
    }

    /** Applies the user settings that influence layout/behaviour. */
    fun applySettings(
        showNumberRow: Boolean,
        suggestionsEnabled: Boolean,
    ) = _state.update {
        if (it.showNumberRow == showNumberRow && it.suggestionsEnabled == suggestionsEnabled) {
            it
        } else {
            it.copy(showNumberRow = showNumberRow, suggestionsEnabled = suggestionsEnabled)
        }
    }

    /** Marks whether Enter should be styled as the accent action for the current field. */
    fun setEnterIsAccent(accent: Boolean) = _state.update {
        if (it.enterIsAccent == accent) it else it.copy(enterIsAccent = accent)
    }

    /** Marks whether the current field is an email field (comma becomes "@"). */
    fun setEmailField(isEmail: Boolean) = _state.update {
        if (it.isEmailField == isEmail) it else it.copy(isEmailField = isEmail)
    }

    /**
     * Marks whether the edited field currently holds any text. The top strip shows the tools
     * for an empty field and switches to suggestions once typing begins; the service updates
     * this on input start and on every selection/text change.
     */
    fun setHasText(hasText: Boolean) = _state.update {
        if (it.hasText == hasText) it else it.copy(hasText = hasText)
    }

    /** Offers a freshly-copied clipboard text as a one-tap paste chip in the top strip. */
    fun setClipSuggestion(text: String) = _state.update {
        if (it.clipSuggestion == text) it else it.copy(clipSuggestion = text)
    }

    /** Dismisses the clipboard paste chip (on key press, paste, or a stale/empty clip). */
    fun clearClipSuggestion() = _state.update {
        if (it.clipSuggestion == null) it else it.copy(clipSuggestion = null)
    }
}
