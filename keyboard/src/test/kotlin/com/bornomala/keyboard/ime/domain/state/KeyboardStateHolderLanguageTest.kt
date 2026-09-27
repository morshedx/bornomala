package com.bornomala.keyboard.ime.domain.state

import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.ShiftState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeyboardStateHolderLanguageTest {

    @Test
    fun `switching language drops a one-shot shift, since Avro capitals are different letters`() {
        val holder = KeyboardStateHolder()
        holder.setLanguage(KeyboardLanguage.ENGLISH)
        holder.setShift(ShiftState.SHIFTED)

        holder.setLanguage(KeyboardLanguage.BANGLA)

        assertThat(holder.current.shift).isEqualTo(ShiftState.OFF)
    }

    @Test
    fun `switching language keeps a deliberate caps lock`() {
        val holder = KeyboardStateHolder()
        holder.setLanguage(KeyboardLanguage.ENGLISH)
        holder.setShift(ShiftState.CAPS_LOCK)

        holder.setLanguage(KeyboardLanguage.BANGLA)

        assertThat(holder.current.shift).isEqualTo(ShiftState.CAPS_LOCK)
    }
}
