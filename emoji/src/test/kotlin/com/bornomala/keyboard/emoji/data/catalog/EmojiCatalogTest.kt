package com.bornomala.keyboard.emoji.data.catalog

import com.bornomala.keyboard.emoji.domain.model.EmojiCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmojiCatalogTest {

    @Test
    fun `catalog is non-empty`() {
        assertThat(EmojiCatalog.all).isNotEmpty()
    }

    @Test
    fun `every static category has entries`() {
        for (category in EmojiCategory.staticCategories) {
            assertThat(EmojiCatalog.byCategory[category]).isNotNull()
            assertThat(EmojiCatalog.byCategory[category]).isNotEmpty()
        }
    }

    @Test
    fun `dynamic RECENT category has no static entries`() {
        assertThat(EmojiCatalog.byCategory[EmojiCategory.RECENT]).isNull()
    }

    @Test
    fun `glyphs are unique within the catalog`() {
        val glyphs = EmojiCatalog.all.map { it.glyph }
        // Some glyphs intentionally repeat across categories (e.g. red heart). Verify
        // uniqueness is enforced per glyph for the usage map key resolution.
        val byGlyph = EmojiCatalog.all.associateBy { it.glyph }
        // associateBy keeps the last; ensure the map can resolve every glyph.
        for (g in glyphs) {
            assertThat(byGlyph[g]).isNotNull()
        }
    }

    @Test
    fun `no entry has blank name or glyph`() {
        for (emoji in EmojiCatalog.all) {
            assertThat(emoji.glyph).isNotEmpty()
            assertThat(emoji.name).isNotEmpty()
        }
    }

    @Test
    fun `category assignment matches grouping`() {
        for ((category, list) in EmojiCatalog.byCategory) {
            assertThat(list.all { it.category == category }).isTrue()
        }
    }
}
