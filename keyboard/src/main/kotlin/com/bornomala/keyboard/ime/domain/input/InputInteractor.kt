package com.bornomala.keyboard.ime.domain.input

import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.KeyboardPage
import com.bornomala.keyboard.ime.domain.model.ShiftState
import com.bornomala.keyboard.ime.domain.port.EditorPort
import com.bornomala.keyboard.ime.domain.port.TransliterationPort
import com.bornomala.keyboard.ime.domain.state.KeyboardStateHolder

/**
 * Pure, framework-free input state machine. Given a [KeyAction], it mutates the editor
 * (through [EditorPort]) and the keyboard state (through [KeyboardStateHolder]), handling:
 *
 *  - character emission with shift/caps casing,
 *  - Bangla composing via the [TransliterationPort] (composing region updated per key),
 *  - backspace (selection-aware, and shrinking the Bangla buffer),
 *  - space with the double-space -> ". " shortcut,
 *  - auto-capitalization at sentence starts,
 *  - page / language / shift toggles.
 *
 * Hot-path discipline: the only mutable per-word allocation is a reused [StringBuilder]
 * for the Bangla latin buffer; ASCII character emission commits a single-char string with
 * no further allocation. No regex, no collections built per key.
 *
 * The interactor reports two things back to its host via [callbacks]: when a word is
 * committed (so the suggestion engine can learn and predict the next word) and when the
 * active word changes (so suggestions can be refreshed). The host runs those reactions on
 * a coroutine off the main thread.
 */
class InputInteractor(
    private val editor: EditorPort,
    private val transliteration: TransliterationPort,
    private val stateHolder: KeyboardStateHolder,
    private val callbacks: Callbacks,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Side-effect hooks the host implements; all are called on the input (main) thread. */
    interface Callbacks {
        /** A whole word was just committed; [language] is the language it was typed in. */
        fun onWordCommitted(language: KeyboardLanguage, word: String)

        /** The in-progress word changed (including becoming empty); refresh suggestions. */
        fun onComposingChanged(language: KeyboardLanguage, currentWord: String)

        /** The user asked to open the emoji panel. */
        fun onEmojiRequested()

        /** The user held the language key: show the system input-method picker. */
        fun onShowImePicker()

        /** Provide cheap haptic/sound feedback for a key press if enabled. */
        fun onFeedback(action: KeyAction)
    }

    private var config: InputConfig = InputConfig()

    /** Reused buffer holding the latin characters of the in-progress word (Bangla mode). */
    private val composingBuffer = StringBuilder(32)

    /** Timestamp of the last committed space, for the double-space-period shortcut. */
    private var lastSpaceTime: Long = 0L

    /** The most recent auto-correct, so an immediate backspace can revert it; null otherwise. */
    private var pendingAutoCorrect: AutoCorrectUndo? = null

    /**
     * True once at least one character has been typed since entering a symbol page. Lets a
     * following space "finish" the symbol and hop back to the alphabetic page, so going to
     * symbols for a single mark (e.g. "?") returns to QWERTY automatically after the space.
     */
    private var symbolCharTyped: Boolean = false

    /** Records an applied auto-correction for one-tap undo (original typed word vs the swap-in). */
    private data class AutoCorrectUndo(val original: String, val corrected: String)

    /** Updates behavioural config (snapshot of user settings). Cheap; no reset. */
    fun updateConfig(newConfig: InputConfig) {
        config = newConfig
    }

    /** Clears the composing buffer/state, e.g. on field change or cursor jump. */
    fun resetComposing() {
        if (composingBuffer.isNotEmpty()) composingBuffer.setLength(0)
        pendingAutoCorrect = null
        symbolCharTyped = false
        editor.finishComposing()
        stateHolder.clearComposingAndSuggestions()
    }

    /**
     * Entry point: process a single key action. Returns immediately after mutating the
     * editor and state. Designed to stay well under the 16ms budget.
     */
    fun onKey(action: KeyAction) {
        callbacks.onFeedback(action)
        // Any key press dismisses a clipboard chip and hands the strip back to suggestions.
        if (stateHolder.current.clipSuggestion != null) stateHolder.clearClipSuggestion()
        // A backspace immediately after an auto-correct reverts it (restores the typed word);
        // any other key just consumes the pending undo. Either way the flag clears here.
        val undo = pendingAutoCorrect
        if (undo != null) {
            pendingAutoCorrect = null
            if (action == KeyAction.Backspace && revertAutoCorrect(undo)) return
        }
        when (action) {
            is KeyAction.Character -> onCharacter(action.char)
            is KeyAction.Text -> onText(action.text)
            KeyAction.Backspace -> onBackspace()
            KeyAction.Space -> onSpace()
            KeyAction.Enter -> onEnter()
            KeyAction.Shift -> stateHolder.toggleShift()
            KeyAction.SwitchLanguage -> onSwitchLanguage()
            KeyAction.ToSymbols -> { commitComposing(); symbolCharTyped = false; stateHolder.showSymbols() }
            KeyAction.ToAlpha -> { symbolCharTyped = false; stateHolder.showAlpha() }
            KeyAction.ToggleSymbolsPage -> stateHolder.toggleSymbolsPage()
            KeyAction.Emoji -> { commitComposing(); callbacks.onEmojiRequested() }
            KeyAction.ShowImePicker -> { commitComposing(); callbacks.onShowImePicker() }
            KeyAction.None -> Unit
        }
    }

    /** Commits a suggestion chosen from the suggestion bar, replacing the current word. */
    fun commitSuggestion(text: String) {
        val state = stateHolder.current
        if (state.isComposing) {
            // Replace the composing region with the chosen word.
            editor.setComposingText(text)
            editor.finishComposing()
        } else {
            editor.commitText(text)
        }
        composingBuffer.setLength(0)
        stateHolder.clearComposingAndSuggestions()
        callbacks.onWordCommitted(state.language, text)
        // After committing a word, append a space and refresh next-word predictions.
        editor.commitText(" ")
        callbacks.onComposingChanged(state.language, "")
        maybeAutoCapitalize()
    }

    // --- character handling ---------------------------------------------------------

    private fun onCharacter(rawChar: Char) {
        val state = stateHolder.current
        // A character typed on a symbol page arms the "space returns to QWERTY" behaviour.
        if (state.page == KeyboardPage.SYMBOLS || state.page == KeyboardPage.SYMBOLS_EXTRA) {
            symbolCharTyped = true
        }
        val isLetter = rawChar.isLetter()
        val cased = if (isLetter && state.shift.isUpper) rawChar.uppercaseChar() else rawChar

        if (state.language == KeyboardLanguage.BANGLA &&
            config.banglaTransliteration &&
            isAsciiLetter(rawChar)
        ) {
            // Build up the latin buffer and show its Bangla rendering in the composing region.
            composingBuffer.append(if (state.shift.isUpper) rawChar.uppercaseChar() else rawChar)
            val rendered = transliteration.transliterate(composingBuffer.toString())
            editor.setComposingText(rendered)
            stateHolder.setComposing(rendered)
            stateHolder.consumeShiftAfterChar()
            callbacks.onComposingChanged(state.language, composingBuffer.toString())
            return
        }

        // English (or non-letter in Bangla): commit directly. For English letters we keep a
        // composing region so the dictionary can offer current-word completions.
        if (state.language == KeyboardLanguage.ENGLISH && isLetter && config.suggestionsEnabled) {
            composingBuffer.append(cased)
            editor.setComposingText(composingBuffer.toString())
            stateHolder.setComposing(composingBuffer.toString())
            stateHolder.consumeShiftAfterChar()
            callbacks.onComposingChanged(state.language, composingBuffer.toString())
            return
        }

        // Plain commit (digits, punctuation, symbols, or letters with suggestions off).
        commitComposing()
        // Smart punctuation: a hugging mark typed right after a (usually auto-inserted) space
        // absorbs that space, so "word ," becomes "word, " — e.g. after picking a suggestion.
        if (cased in SPACE_ABSORBING_PUNCTUATION && editor.textBeforeCursor(1) == " ") {
            editor.deleteSurroundingText(1, 0)
            editor.commitText("$cased ")
            maybeAutoCapitalize()
            return
        }
        editor.commitText(cased.toString())
        if (isLetter) stateHolder.consumeShiftAfterChar()
        // Punctuation that ends a sentence may re-arm auto-capitalization on next space.
    }

    private fun onText(text: String) {
        commitComposing()
        editor.commitText(text)
    }

    // --- backspace ------------------------------------------------------------------

    private fun onBackspace() {
        val state = stateHolder.current
        if (state.isComposing && composingBuffer.isNotEmpty()) {
            // Shrink the in-progress word by one latin char and re-render.
            composingBuffer.setLength(composingBuffer.length - 1)
            if (composingBuffer.isEmpty()) {
                editor.setComposingText("")
                editor.finishComposing()
                stateHolder.clearComposingAndSuggestions()
                callbacks.onComposingChanged(state.language, "")
            } else {
                val rendered = if (state.language == KeyboardLanguage.BANGLA && config.banglaTransliteration) {
                    transliteration.transliterate(composingBuffer.toString())
                } else {
                    composingBuffer.toString()
                }
                editor.setComposingText(rendered)
                stateHolder.setComposing(rendered)
                callbacks.onComposingChanged(state.language, composingBuffer.toString())
            }
            return
        }
        // No composing word: delegate to the editor (handles selection vs single char).
        editor.backspace()
    }

    // --- space / enter --------------------------------------------------------------

    private fun onSpace() {
        commitComposing()
        val now = clock()
        if (config.doubleSpacePeriod && now - lastSpaceTime <= config.doubleSpaceWindowMs) {
            // Turn the previously committed space + this one into ". ".
            val before = editor.textBeforeCursor(2)
            if (before.length >= 1 && before.last() == ' ' && endsSentencePunctuationAbsent(before)) {
                editor.deleteSurroundingText(1, 0)
                editor.commitText(". ")
                lastSpaceTime = 0L
                maybeAutoCapitalize()
                returnToAlphaAfterSymbolSpace()
                return
            }
        }
        editor.commitText(" ")
        lastSpaceTime = now
        maybeAutoCapitalize()
        returnToAlphaAfterSymbolSpace()
    }

    /**
     * After a symbol was typed on a symbol page, a following space "finishes" it and returns
     * to the alphabetic page — so a quick trip to symbols for one mark (e.g. "?") hops back to
     * QWERTY automatically. No-op on the alphabetic page or before any symbol is typed.
     */
    private fun returnToAlphaAfterSymbolSpace() {
        if (!symbolCharTyped) return
        val page = stateHolder.current.page
        if (page == KeyboardPage.SYMBOLS || page == KeyboardPage.SYMBOLS_EXTRA) {
            symbolCharTyped = false
            stateHolder.showAlpha()
        }
    }

    private fun onEnter() {
        commitComposing()
        editor.sendDefaultEditorActionOrNewline()
        // A newline starts a new sentence: re-arm auto-cap.
        if (config.autoCapitalization && stateHolder.current.language == KeyboardLanguage.ENGLISH) {
            stateHolder.setShift(ShiftState.SHIFTED)
        }
    }

    private fun onSwitchLanguage() {
        commitComposing()
        stateHolder.cycleLanguage()
        callbacks.onComposingChanged(stateHolder.current.language, "")
    }

    /**
     * Moves the text caret by [delta] characters (negative = left), driven by the spacebar
     * hold-and-swipe gesture. Finalizes any in-progress word in place — verbatim, with no
     * surprise auto-correct — and clears suggestions so the caret never lands inside a composing
     * region. Mirrors the volume-key cursor move. The gesture layer debounces the drag into whole
     * characters, so this only fires when the caret actually advances.
     */
    fun onCursorSwipe(delta: Int) {
        if (delta == 0) return
        resetComposing()
        editor.moveCursorBy(delta)
    }

    // --- helpers --------------------------------------------------------------------

    /**
     * Finalizes any in-progress word: commits the composing region as text, records it for
     * learning, and clears the buffer. Safe to call when nothing is composing.
     */
    private fun commitComposing() {
        val state = stateHolder.current
        if (!state.isComposing) {
            composingBuffer.setLength(0)
            return
        }
        val verbatim = state.composingText
        // Auto-correct: if the strip flagged a high-confidence target — an English spelling
        // correction, or the top Bangla phonetic-dictionary word (e.g. chara -> ছাড়া) — swap it
        // into the composing region before finalizing, and remember it so backspace can revert.
        // English spell auto-correct is gated by its own toggle; Bangla phonetic auto-commit is
        // not (it is the core of phonetic typing and has its own Bangla setting).
        val autoCorrectAllowed = config.suggestionsEnabled &&
            (state.language != KeyboardLanguage.ENGLISH || config.autoCorrectEnabled)
        val correction = if (autoCorrectAllowed) {
            state.suggestions.firstOrNull { it.isAutoCorrect }?.text
        } else {
            null
        }
        val committedWord: String
        if (correction != null && correction != verbatim) {
            editor.setComposingText(correction)
            editor.finishComposing()
            committedWord = correction
            pendingAutoCorrect = AutoCorrectUndo(original = verbatim, corrected = correction)
        } else {
            editor.finishComposing()
            committedWord = verbatim
        }
        composingBuffer.setLength(0)
        stateHolder.clearComposingAndSuggestions()
        if (committedWord.isNotEmpty()) {
            callbacks.onWordCommitted(state.language, committedWord)
            callbacks.onComposingChanged(state.language, "")
        }
    }

    /**
     * Reverts the last auto-correction: if the text immediately before the cursor is the
     * corrected word followed by a single space (the state right after auto-correct + space),
     * deletes both and re-commits the original typed word. Returns true when it reverted.
     */
    private fun revertAutoCorrect(undo: AutoCorrectUndo): Boolean {
        val tail = undo.corrected + " "
        val before = editor.textBeforeCursor(tail.length)
        if (before.toString() != tail) return false
        editor.deleteSurroundingText(tail.length, 0)
        editor.commitText(undo.original)
        callbacks.onComposingChanged(stateHolder.current.language, "")
        return true
    }

    /** Re-arms shift for sentence-start capitalization in English when enabled. */
    private fun maybeAutoCapitalize() {
        if (!config.autoCapitalization) return
        if (stateHolder.current.language != KeyboardLanguage.ENGLISH) return
        if (stateHolder.current.shift == ShiftState.CAPS_LOCK) return
        val before = editor.textBeforeCursor(3)
        if (shouldCapitalizeAfter(before)) {
            stateHolder.setShift(ShiftState.SHIFTED)
        } else {
            stateHolder.setShift(ShiftState.OFF)
        }
    }

    private fun shouldCapitalizeAfter(before: CharSequence): Boolean {
        if (before.isEmpty()) return true // start of field
        // Pattern: sentence-ending punctuation followed by space(s) -> capitalize.
        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) return true
        val last = trimmed.last()
        val hadTrailingSpace = before.last() == ' '
        return hadTrailingSpace && (last == '.' || last == '!' || last == '?')
    }

    /** True when there is no sentence-ending punctuation just before the trailing space. */
    private fun endsSentencePunctuationAbsent(before: CharSequence): Boolean {
        if (before.length < 2) return true
        val charBeforeSpace = before[before.length - 2]
        return charBeforeSpace != '.' && charBeforeSpace != '!' && charBeforeSpace != '?'
    }

    private fun isAsciiLetter(c: Char): Boolean = (c in 'a'..'z') || (c in 'A'..'Z')

    private fun Char.isLetter(): Boolean = Character.isLetter(this)
}

/** Punctuation that should "hug" the preceding word, absorbing an auto-inserted space before it. */
private val SPACE_ABSORBING_PUNCTUATION: Set<Char> = setOf(',', '.', '!', '?', ';', ':')
