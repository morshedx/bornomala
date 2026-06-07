package com.bornomala.keyboard.transliteration.domain.engine

import com.bornomala.keyboard.transliteration.domain.model.TransliterationResult

/**
 * Stateful, per-word phonetic input engine for Bangla (Avro-style).
 *
 * This is the stable contract from the spec — do not change the signatures without strong
 * reason; it is the extension seam other modules (the IME, suggestions) build on.
 *
 * Contract / threading:
 *  - The engine owns a small Latin composition buffer. [processInput] appends the typed
 *    Latin text, [delete] removes one buffer unit (backspace), [reset] clears the buffer.
 *  - All three methods are synchronous, cheap and allocation-light so they can run inline
 *    on the IME input path within the per-keystroke latency budget. They are NOT thread
 *    safe; call them from a single (input) thread, which the IME guarantees.
 *  - Each call returns a fresh immutable [TransliterationResult] describing the current
 *    composition; the caller renders [TransliterationResult.composed] and commits
 *    [TransliterationResult.commitCandidate] when the word ends.
 */
interface TransliterationEngine {

    /**
     * Appends [input] (one or more Latin characters typed by the user) to the composition
     * buffer and returns the updated rendering.
     *
     * Typically [input] is a single character, but multi-character input (paste of a Latin
     * run, fast key batches) is supported and processed left to right.
     */
    fun processInput(input: String): TransliterationResult

    /**
     * Removes the last logical unit from the composition buffer (backspace) and returns the
     * re-derived rendering. Deletion works at the Latin-buffer level: one user-visible
     * backspace removes one Latin keystroke and the whole Bangla word is recomposed, which
     * keeps matra/conjunct rendering correct.
     *
     * If the buffer is already empty the result is [TransliterationResult.EMPTY] and the
     * caller should forward the backspace to the underlying text instead.
     */
    fun delete(): TransliterationResult

    /**
     * Clears all composition state. Call on word commit, focus change, or cursor moves that
     * leave the composing region.
     */
    fun reset()
}
