package com.bornomala.keyboard.ime.data.editor

import android.view.inputmethod.EditorInfo
import com.bornomala.keyboard.ime.domain.model.EnterAction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EnterActionResolverTest {

    @Test
    fun `declared actions map to their enter action`() {
        val cases = mapOf(
            EditorInfo.IME_ACTION_DONE to EnterAction.DONE,
            EditorInfo.IME_ACTION_GO to EnterAction.GO,
            EditorInfo.IME_ACTION_SEARCH to EnterAction.SEARCH,
            EditorInfo.IME_ACTION_SEND to EnterAction.SEND,
            EditorInfo.IME_ACTION_NEXT to EnterAction.NEXT,
            EditorInfo.IME_ACTION_PREVIOUS to EnterAction.PREVIOUS,
            EditorInfo.IME_ACTION_NONE to EnterAction.NEWLINE,
            EditorInfo.IME_ACTION_UNSPECIFIED to EnterAction.NEWLINE,
        )
        cases.forEach { (imeOptions, expected) ->
            assertThat(EnterActionResolver.resolve(imeOptions)).isEqualTo(expected)
        }
    }

    @Test
    fun `no-enter-action flag forces newline even with a declared action`() {
        val imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertThat(EnterActionResolver.resolve(imeOptions)).isEqualTo(EnterAction.NEWLINE)
    }

    @Test
    fun `unrelated flags do not hide the declared action`() {
        val imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        assertThat(EnterActionResolver.resolve(imeOptions)).isEqualTo(EnterAction.SEARCH)
    }

    @Test
    fun `action id round-trips through resolve`() {
        EnterAction.entries.filter { it != EnterAction.NEWLINE }.forEach { action ->
            assertThat(EnterActionResolver.resolve(EnterActionResolver.actionId(action))).isEqualTo(action)
        }
        assertThat(EnterActionResolver.actionId(EnterAction.NEWLINE))
            .isEqualTo(EditorInfo.IME_ACTION_UNSPECIFIED)
    }
}
