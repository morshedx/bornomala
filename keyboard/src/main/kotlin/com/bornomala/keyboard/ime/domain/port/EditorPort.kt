package com.bornomala.keyboard.ime.domain.port

/**
 * Outbound port abstracting the text field the keyboard is editing. The Android
 * implementation wraps `android.view.inputmethod.InputConnection`; tests use a simple
 * in-memory fake. This is what lets the input logic be unit-tested as pure Kotlin
 * (Robolectric-free) and keeps the interactor free of framework types.
 *
 * All operations are best-effort and must be fast (main thread, hot path). Methods mirror
 * the subset of InputConnection the keyboard actually uses.
 */
interface EditorPort {

    /** Commits final text at the cursor, replacing any active composing region. */
    fun commitText(text: String)

    /**
     * Sets the composing (underlined, in-progress) region to [text]. Used for Bangla
     * transliteration and English auto-correct preview. Passing an empty string clears it.
     */
    fun setComposingText(text: String)

    /** Finalizes the current composing region as committed text (no longer underlined). */
    fun finishComposing()

    /**
     * Deletes [beforeChars] code units before the cursor and [afterChars] after it. When a
     * selection is active the caller deletes the selection instead via [commitText] of "".
     */
    fun deleteSurroundingText(beforeChars: Int, afterChars: Int)

    /** Sends a raw key event (used for Enter/editor-action and hardware-style keys). */
    fun sendDefaultEditorActionOrNewline()

    /** Performs a backspace honouring any active selection (deletes selection if present). */
    fun backspace()

    /**
     * Moves the text caret by [chars] characters (negative = left, positive = right). Implemented
     * as directional key events so it works in every editor (including those that don't expose
     * extracted text), letting the target field apply its own caret/line semantics. Called from
     * the spacebar hold-and-swipe gesture, which is user-paced — not the per-keystroke hot path.
     */
    fun moveCursorBy(chars: Int)

    /** The text immediately before the cursor, up to [n] chars; "" if unavailable. */
    fun textBeforeCursor(n: Int): CharSequence

    /** True when there is currently a non-empty selection. */
    fun hasSelection(): Boolean
}
