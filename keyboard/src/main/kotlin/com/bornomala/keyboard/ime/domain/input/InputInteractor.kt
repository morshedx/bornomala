package com.bornomala.keyboard.ime.domain.input

import com.bornomala.keyboard.ime.domain.model.CapsMode
import com.bornomala.keyboard.ime.domain.model.FieldProfile
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.KeyboardPage
import com.bornomala.keyboard.ime.domain.model.KeyboardState
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

        /**
         * In Bangla mode the user tapped [word] on the strip for the roman input [roman] (the
         * raw-latin chip excluded), so the host can remember the choice for that spelling.
         */
        fun onBanglaPicked(roman: String, word: String) = Unit
    }

    private var config: InputConfig = InputConfig()

    private var fieldProfile: FieldProfile = FieldProfile.DEFAULT

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

    /**
     * A comma typed right after a Bangla word, held back to see whether a second comma follows:
     * Avro's `,,` is an explicit hasant (্ + ZWNJ, `k,,Sh` -> ক্‌ষ). The editor shows the word plus
     * the comma meanwhile; any other key settles it as an ordinary comma (see [commitComposing]).
     */
    private var pendingComma: Boolean = false

    /** Records an applied auto-correction for one-tap undo (original typed word vs the swap-in). */
    private data class AutoCorrectUndo(
        val original: String,
        val corrected: String,
        /** The Bangla roman input behind the swap; empty for English. */
        val roman: String = "",
    )

    /** Updates behavioural config (snapshot of user settings). Cheap; no reset. */
    fun updateConfig(newConfig: InputConfig) {
        config = newConfig
    }

    /**
     * Applies the focused field's requirements (see [FieldProfile]); called on every field
     * change. The field can only switch features off — the user's settings still gate them.
     */
    fun setField(profile: FieldProfile) {
        fieldProfile = profile
    }

    /** Suggestions and current-word composing: on in settings and allowed by the field. */
    private val suggestionsOn: Boolean
        get() = config.suggestionsEnabled && fieldProfile.allowSuggestions

    /** Clears the composing buffer/state, e.g. on field change or cursor jump. */
    fun resetComposing() {
        if (composingBuffer.isNotEmpty()) composingBuffer.setLength(0)
        pendingAutoCorrect = null
        pendingComma = false
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

    /**
     * Shows the word that space will commit in the composing region as soon as the strip for
     * [roman] is ready — `chair` reads চেয়ার while typing, not the letter-by-letter ছাইর — so what
     * the user sees is what they get. Bangla only, and only when auto-correction may apply, since
     * otherwise space keeps the rendering. Ignored when [roman] is no longer what is being typed
     * (a late result for an earlier keystroke).
     */
    fun previewBanglaAutoPick(roman: String) {
        val state = stateHolder.current
        if (state.language != KeyboardLanguage.BANGLA || !state.isComposing) return
        if (composingBuffer.length != roman.length || !composingBuffer.contentEquals(roman)) return
        val shown = autoCorrection(state) ?: state.composingText
        editor.setComposingText(if (pendingComma) "$shown," else shown)
    }

    /** Commits a suggestion chosen from the suggestion bar, replacing the current word. */
    fun commitSuggestion(text: String) {
        val state = stateHolder.current
        if (state.language == KeyboardLanguage.BANGLA && composingBuffer.isNotEmpty()) {
            val roman = composingBuffer.toString()
            if (text != roman) callbacks.onBanglaPicked(roman, text)
        }
        val comma = pendingComma
        pendingComma = false
        if (state.isComposing) {
            // Replace the composing region (including any held comma) with the chosen word.
            editor.setComposingText(text)
            editor.finishComposing()
        } else {
            editor.commitText(text)
        }
        if (comma) editor.commitText(",")
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

        if (pendingComma) {
            if (rawChar == ',') {
                // Second comma: Avro's explicit hasant joins the word being typed.
                pendingComma = false
                composingBuffer.append(",,")
                renderBanglaComposing(state.language)
                return
            }
            // Anything else: the held comma was an ordinary one. Finish the word and the comma
            // exactly as typing them apart would, then handle this key from a clean slate.
            commitComposing()
            onCharacter(rawChar)
            return
        }
        if (rawChar == ',' && state.language == KeyboardLanguage.BANGLA &&
            config.banglaTransliteration && composingBuffer.isNotEmpty()
        ) {
            pendingComma = true
            editor.setComposingText(state.composingText + ",")
            return
        }

        if (state.language == KeyboardLanguage.BANGLA &&
            config.banglaTransliteration &&
            (isAsciiLetter(rawChar) || isMidWordAvroSymbol(rawChar))
        ) {
            // Build up the latin buffer and show its Bangla rendering in the composing region.
            composingBuffer.append(if (state.shift.isUpper) rawChar.uppercaseChar() else rawChar)
            val rendered = transliteration.transliterate(composingBuffer.toString())
            editor.setComposingText(rendered)
            stateHolder.setComposing(rendered)
            consumeShift()
            callbacks.onComposingChanged(state.language, composingBuffer.toString())
            return
        }

        // English (or non-letter in Bangla): commit directly. For English letters we keep a
        // composing region so the dictionary can offer current-word completions.
        if (state.language == KeyboardLanguage.ENGLISH && isLetter && suggestionsOn) {
            composingBuffer.append(cased)
            editor.setComposingText(composingBuffer.toString())
            stateHolder.setComposing(composingBuffer.toString())
            consumeShift()
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
        if (isLetter) consumeShift()
        // Punctuation that ends a sentence may re-arm auto-capitalization on next space.
    }

    private fun onText(text: String) {
        commitComposing()
        editor.commitText(text)
    }

    // --- backspace ------------------------------------------------------------------

    private fun onBackspace() {
        val state = stateHolder.current
        if (pendingComma) {
            // Delete just the held comma; the word stays in progress.
            pendingComma = false
            editor.setComposingText(state.composingText)
            return
        }
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
        if (config.doubleSpacePeriod && fieldProfile.allowAutoCorrect && now - lastSpaceTime <= config.doubleSpaceWindowMs) {
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
        // A newline starts a new sentence (and word): re-arm auto-cap where the field wants it.
        if (config.autoCapitalization && fieldProfile.capsMode != CapsMode.NONE &&
            stateHolder.current.language == KeyboardLanguage.ENGLISH &&
            stateHolder.current.shift != ShiftState.CAPS_LOCK
        ) {
            stateHolder.setShift(ShiftState.SHIFTED)
        }
    }

    private fun onSwitchLanguage() {
        commitComposing()
        stateHolder.cycleLanguage()
        callbacks.onComposingChanged(stateHolder.current.language, "")
        // Switching back to English re-arms sentence-start capitalization where it applies.
        maybeAutoCapitalize()
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
        val comma = pendingComma
        if (comma) {
            // Take the held comma out of the composing region; it is committed after the word.
            pendingComma = false
            editor.setComposingText(verbatim)
        }
        // Auto-correct: if the strip flagged a high-confidence target — an English spelling
        // correction, or the top Bangla phonetic-dictionary word (e.g. chara -> ছাড়া) — swap it
        // into the composing region before finalizing, and remember it so backspace can revert.
        // Both languages obey the auto-correction setting: with it off, space commits exactly
        // what was typed and the alternatives stay one tap away on the suggestion strip.
        val correction = autoCorrection(state)
        val committedWord: String
        if (correction != null && correction != verbatim) {
            editor.setComposingText(correction)
            editor.finishComposing()
            committedWord = correction
            val roman = if (state.language == KeyboardLanguage.BANGLA) composingBuffer.toString() else ""
            pendingAutoCorrect = AutoCorrectUndo(original = verbatim, corrected = correction, roman = roman)
        } else {
            editor.finishComposing()
            committedWord = verbatim
        }
        if (comma) editor.commitText(",")
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
        // Undoing a Bangla swap is a deliberate choice of what was typed: remember it so the same
        // spelling is not swapped again (the auto-pick defers to picks, not to learned words).
        if (undo.roman.isNotEmpty()) callbacks.onBanglaPicked(undo.roman, undo.original)
        callbacks.onComposingChanged(stateHolder.current.language, "")
        return true
    }

    /** Drops a one-shot shift after a letter; all-caps fields re-arm it straight away. */
    private fun consumeShift() {
        stateHolder.consumeShiftAfterChar()
        if (fieldProfile.capsMode == CapsMode.CHARACTERS) maybeAutoCapitalize()
    }

    /**
     * Re-arms shift at the start of a field; the host calls it when a field is bound, after
     * [setField], so an empty field starts capitalized when the field asks for it.
     */
    fun refreshAutoCapitalization() = maybeAutoCapitalize()

    /** Re-arms shift for sentence-start capitalization in English when enabled. */
    private fun maybeAutoCapitalize() {
        if (!config.autoCapitalization) return
        if (stateHolder.current.language != KeyboardLanguage.ENGLISH) return
        if (stateHolder.current.shift == ShiftState.CAPS_LOCK) return
        val before = editor.textBeforeCursor(3)
        val capitalize = when (fieldProfile.capsMode) {
            CapsMode.NONE -> false
            CapsMode.CHARACTERS -> true
            CapsMode.WORDS -> before.isEmpty() || before.last().isWhitespace()
            CapsMode.SENTENCES -> shouldCapitalizeAfter(before)
        }
        if (capitalize) {
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

    /**
     * The word space swaps in for the one being composed, or null to keep it as typed. A field's
     * "don't rewrite" restriction (URLs, email) protects English text as typed; it does not apply
     * to Bangla, where the roman input is never the final text, so choosing the Bangla word
     * (computer -> কম্পিউটার) is transliteration, not a rewrite. Passwords are still covered —
     * they get no suggestions at all.
     */
    private fun autoCorrection(state: KeyboardState): String? {
        val fieldAllows = fieldProfile.allowAutoCorrect || state.language == KeyboardLanguage.BANGLA
        if (!suggestionsOn || !config.autoCorrectEnabled || !fieldAllows) return null
        return state.suggestions.firstOrNull { it.isAutoCorrect }?.text
    }

    /** Re-renders the Bangla roman buffer into the composing region and refreshes suggestions. */
    private fun renderBanglaComposing(language: KeyboardLanguage) {
        val rendered = transliteration.transliterate(composingBuffer.toString())
        editor.setComposingText(rendered)
        stateHolder.setComposing(rendered)
        callbacks.onComposingChanged(language, composingBuffer.toString())
    }

    /**
     * Avro symbols that only mean something inside a word — `^` chandrabindu (`cha^d` -> চাঁদ),
     * `:` visarga (`du:kho` -> দুঃখ), and the `` ` `` conjunct breaker (`t``` -> ৎ). They join the
     * roman buffer only once a word has started, so a leading `:` or `^` still types itself.
     */
    private fun isMidWordAvroSymbol(c: Char): Boolean =
        composingBuffer.isNotEmpty() && MID_WORD_AVRO_SYMBOLS.indexOf(c) >= 0

    private fun Char.isLetter(): Boolean = Character.isLetter(this)
}

/** Punctuation that should "hug" the preceding word, absorbing an auto-inserted space before it. */
private val SPACE_ABSORBING_PUNCTUATION: Set<Char> = setOf(',', '.', '!', '?', ';', ':')

/** Avro symbols accepted inside a Bangla word; see `InputInteractor.isMidWordAvroSymbol`. */
private const val MID_WORD_AVRO_SYMBOLS = "^:`"
