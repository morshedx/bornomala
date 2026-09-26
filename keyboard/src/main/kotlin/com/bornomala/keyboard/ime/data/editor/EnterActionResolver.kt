package com.bornomala.keyboard.ime.data.editor

import android.view.inputmethod.EditorInfo
import com.bornomala.keyboard.ime.domain.model.EnterAction

/**
 * Maps an editor's `imeOptions` to the [EnterAction] the Enter key should show and perform, and
 * back to the framework action id. Pure integer logic (the `EditorInfo` constants are inlined), so
 * it is unit-testable on the JVM.
 */
object EnterActionResolver {

    /**
     * Resolves the Enter behaviour for [imeOptions]. `IME_FLAG_NO_ENTER_ACTION` wins over any
     * declared action: multi-line fields (e.g. a notes body) set it so Enter inserts a newline.
     */
    fun resolve(imeOptions: Int): EnterAction {
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterAction.NEWLINE
        return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
            EditorInfo.IME_ACTION_GO -> EnterAction.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
            EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
            else -> EnterAction.NEWLINE
        }
    }

    /** The `EditorInfo.IME_ACTION_*` id for [action]; `IME_ACTION_UNSPECIFIED` for a newline. */
    fun actionId(action: EnterAction): Int = when (action) {
        EnterAction.NEWLINE -> EditorInfo.IME_ACTION_UNSPECIFIED
        EnterAction.DONE -> EditorInfo.IME_ACTION_DONE
        EnterAction.GO -> EditorInfo.IME_ACTION_GO
        EnterAction.SEARCH -> EditorInfo.IME_ACTION_SEARCH
        EnterAction.SEND -> EditorInfo.IME_ACTION_SEND
        EnterAction.NEXT -> EditorInfo.IME_ACTION_NEXT
        EnterAction.PREVIOUS -> EditorInfo.IME_ACTION_PREVIOUS
    }
}
