package com.bornomala.keyboard.ime.domain.input

import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
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

        /** Provide cheap haptic/sound feedback for a key press if enabled. */
        fun onFeedback(action: KeyAction)
    }

    private var config: InputConfig = InputConfig()

    /** Reused buffer holding the latin characters of the in-progress word (Bangla mode). */
    private val composingBuffer = StringBuilder(32)

    /** Timestamp of the last committed space, for the double-space-period shortcut. */
    private var lastSpaceTime: Long = 0L

    /** Updates behavioural config (snapshot of user settings). Cheap; no reset. */
    fun updateConfig(newConfig: InputConfig) {
        config = newConfig
    }

    /** Clears the composing buffer/state, e.g. on field change or cursor jump. */
    fun resetComposing() {
        if (composingBuffer.isNotEmpty()) composingBuffer.setLength(0)
        editor.finishComposing()
        stateHolder.clearComposingAndSuggestions()
    }

    /**
     * Entry point: process a single key action. Returns immediately after mutating the
     * editor and state. Designed to stay well under the 16ms budget.
     */
    fun onKey(action: KeyAction) {
        callbacks.onFeedback(action)
        when (action) {
            is KeyAction.Character -> onCharacter(action.char)
            is KeyAction.Text -> onText(action.text)
            KeyAction.Backspace -> onBackspace()
            KeyAction.Space -> onSpace()
            KeyAction.Enter -> onEnter()
            KeyAction.Shift -> stateHolder.toggleShift()
            KeyAction.SwitchLanguage -> onSwitchLanguage()
            KeyAction.ToSymbols -> { commitComposing(); stateHolder.showSymbols() }
            KeyAction.ToAlpha -> stateHolder.showAlpha()
            KeyAction.ToggleSymbolsPage -> stateHolder.toggleSymbolsPage()
            KeyAction.Emoji -> { commitComposing(); callbacks.onEmojiRequested() }
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
                return
            }
        }
        editor.commitText(" ")
        lastSpaceTime = now
        maybeAutoCapitalize()
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
        editor.finishComposing()
        val committedWord = state.composingText
        val sourceWord = composingBuffer.toString().ifEmpty { committedWord }
        composingBuffer.setLength(0)
        stateHolder.clearComposingAndSuggestions()
        if (committedWord.isNotEmpty()) {
            callbacks.onWordCommitted(state.language, committedWord)
            callbacks.onComposingChanged(state.language, "")
        }
        // sourceWord retained for potential future learning of latin->bangla mappings.
        @Suppress("UNUSED_EXPRESSION") sourceWord
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
