package com.bornomala.keyboard.ime.data.layout

import com.bornomala.keyboard.ime.domain.model.FieldKind
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.KeyboardPage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutProviderFieldTest {

    private val provider = LayoutProvider()

    private fun bottomActions(field: FieldKind, page: KeyboardPage = KeyboardPage.ALPHA) =
        provider.layoutFor(KeyboardLanguage.ENGLISH, page, showNumberRow = false, field = field)
            .rows.last().keys.map { it.action }

    @Test
    fun `each field kind gets its own bottom-row keys`() {
        assertThat(bottomActions(FieldKind.TEXT)).contains(KeyAction.Character(','))
        assertThat(bottomActions(FieldKind.EMAIL)).contains(KeyAction.Character('@'))
        val url = bottomActions(FieldKind.URL)
        assertThat(url).contains(KeyAction.Character('/'))
        assertThat(url).contains(KeyAction.Text(".com"))
        assertThat(url).doesNotContain(KeyAction.Character(','))
    }

    @Test
    fun `url row keeps the same total width`() {
        fun width(field: FieldKind) = provider.layoutFor(KeyboardLanguage.ENGLISH, KeyboardPage.ALPHA, false, field)
            .rows.last().keys.sumOf { it.weight.toDouble() }
        assertThat(width(FieldKind.URL)).isEqualTo(width(FieldKind.TEXT))
    }

    @Test
    fun `field keys only change the alpha page`() {
        assertThat(bottomActions(FieldKind.URL, KeyboardPage.SYMBOLS))
            .isEqualTo(bottomActions(FieldKind.TEXT, KeyboardPage.SYMBOLS))
    }
}
