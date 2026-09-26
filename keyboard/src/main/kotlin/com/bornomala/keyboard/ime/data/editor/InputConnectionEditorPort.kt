package com.bornomala.keyboard.ime.data.editor

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.bornomala.keyboard.ime.domain.model.EnterAction
import com.bornomala.keyboard.ime.domain.port.EditorPort

/**
 * [EditorPort] backed by an Android [InputConnection]. The IME service swaps the live
 * [connection] on every `onStartInput` / `onCreateInputView`; all operations are no-ops when
 * no connection is attached (e.g. between fields), so the input logic never NPEs.
 *
 * Every method maps to the minimal subset of [InputConnection] the keyboard needs and is
 * cheap enough for the per-keystroke hot path. No allocation beyond the framework calls.
 */
class InputConnectionEditorPort : EditorPort {

    /** The currently bound input connection; null between fields. Written on the input thread. */
    @Volatile
    var connection: InputConnection? = null

    /** The bound field's Enter behaviour; set alongside [connection] on every `onStartInput`. */
    @Volatile
    var enterAction: EnterAction = EnterAction.NEWLINE

    /** The app's own action id behind a custom Enter label; wins over [enterAction] when set. */
    @Volatile
    var customActionId: Int? = null

    override fun commitText(text: String) {
        connection?.commitText(text, 1)
    }

    override fun setComposingText(text: String) {
        connection?.setComposingText(text, 1)
    }

    override fun finishComposing() {
        connection?.finishComposingText()
    }

    override fun deleteSurroundingText(beforeChars: Int, afterChars: Int) {
        connection?.deleteSurroundingText(beforeChars, afterChars)
    }

    override fun sendDefaultEditorActionOrNewline() {
        val c = connection ?: return
        customActionId?.let {
            c.performEditorAction(it)
            return
        }
        val action = enterAction
        if (action != EnterAction.NEWLINE) {
            // Fire the field's declared action directly, as Gboard does: some editors (Compose,
            // Flutter, React Native) ignore a raw Enter key event for done/search/go.
            c.performEditorAction(EnterActionResolver.actionId(action))
            return
        }
        // Otherwise a real key event: multi-line fields insert a newline and single-line fields
        // without a declared action still get their default Enter handling.
        c.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        c.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun backspace() {
        val c = connection ?: return
        if (hasSelection()) {
            // Replacing the selection with empty text deletes exactly the selected range.
            c.commitText("", 1)
        } else {
            c.deleteSurroundingText(1, 0)
        }
    }

    override fun moveCursorBy(chars: Int) {
        val c = connection ?: return
        if (chars == 0) return
        // Directional key events move the caret in any editor (unlike setSelection, which needs
        // extracted-text support some fields lack). One down+up per step; |chars| is small (a few
        // characters per gesture frame), and the gesture itself is user-paced, so the per-step
        // KeyEvent allocation is off the typing hot path.
        val keyCode = if (chars > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(if (chars > 0) chars else -chars) {
            c.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            c.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    override fun textBeforeCursor(n: Int): CharSequence =
        connection?.getTextBeforeCursor(n, 0) ?: ""

    override fun hasSelection(): Boolean =
        connection?.getSelectedText(0)?.isNotEmpty() == true
}
