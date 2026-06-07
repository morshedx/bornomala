package com.bornomala.keyboard.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards against accidental token regressions: light and dark keyboard surfaces must
 * stay visibly distinct, and every role must have a non-transparent face color.
 */
class KeyboardColorsTest {

    @Test
    fun `light and dark keyboard backgrounds differ`() {
        assertThat(LightKeyboardColors.keyboardBackground)
            .isNotEqualTo(DarkKeyboardColors.keyboardBackground)
    }

    @Test
    fun `pressed key color differs from resting key color in both schemes`() {
        assertThat(LightKeyboardColors.keyBackground)
            .isNotEqualTo(LightKeyboardColors.keyBackgroundPressed)
        assertThat(DarkKeyboardColors.keyBackground)
            .isNotEqualTo(DarkKeyboardColors.keyBackgroundPressed)
    }

    @Test
    fun `all key face colors are fully opaque`() {
        listOf(LightKeyboardColors, DarkKeyboardColors).forEach { scheme ->
            assertThat(scheme.keyBackground.alpha).isEqualTo(1f)
            assertThat(scheme.functionalKeyBackground.alpha).isEqualTo(1f)
            assertThat(scheme.accentKeyBackground.alpha).isEqualTo(1f)
        }
    }
}
