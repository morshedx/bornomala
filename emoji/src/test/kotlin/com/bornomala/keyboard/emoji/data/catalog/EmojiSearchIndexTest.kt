package com.bornomala.keyboard.emoji.data.catalog

import com.bornomala.keyboard.emoji.domain.model.Emoji
import com.bornomala.keyboard.emoji.domain.model.EmojiCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmojiSearchIndexTest {

    private val catalog = listOf(
        Emoji("😀", "grinning face", listOf("smile", "happy"), EmojiCategory.SMILEYS),
        Emoji("😂", "face with tears of joy", listOf("lol", "laugh", "haha"), EmojiCategory.SMILEYS),
        Emoji("❤️", "red heart", listOf("love", "heart"), EmojiCategory.SYMBOLS),
        Emoji("🍕", "pizza", listOf("food", "italian"), EmojiCategory.FOOD),
        Emoji("🐶", "dog face", listOf("dog", "puppy", "pet"), EmojiCategory.ANIMALS),
    )

    private val index = EmojiSearchIndex(catalog)

    @Test
    fun `blank query returns empty`() {
        assertThat(index.search("")).isEmpty()
        assertThat(index.search("   ")).isEmpty()
    }

    @Test
    fun `exact name match is found`() {
        val results = index.search("pizza")
        assertThat(results.map { it.glyph }).contains("🍕")
    }

    @Test
    fun `name token prefix match works`() {
        val results = index.search("grin")
        assertThat(results.map { it.glyph }).contains("😀")
    }

    @Test
    fun `keyword match is found`() {
        val results = index.search("lol")
        assertThat(results.map { it.glyph }).contains("😂")
    }

    @Test
    fun `keyword prefix match is found`() {
        val results = index.search("pup")
        assertThat(results.map { it.glyph }).contains("🐶")
    }

    @Test
    fun `search is case insensitive`() {
        assertThat(index.search("PIZZA").map { it.glyph }).contains("🍕")
        assertThat(index.search("Love").map { it.glyph }).contains("❤️")
    }

    @Test
    fun `exact name ranks before keyword-only match`() {
        // "heart" is the name token of red heart and not present elsewhere here.
        val results = index.search("heart")
        assertThat(results.first().glyph).isEqualTo("❤️")
    }

    @Test
    fun `no match returns empty`() {
        assertThat(index.search("zzzznomatch")).isEmpty()
    }

    @Test
    fun `real catalog search returns relevant emoji`() {
        val realIndex = EmojiSearchIndex(EmojiCatalog.all)
        val results = realIndex.search("heart")
        assertThat(results).isNotEmpty()
        assertThat(results.any { it.glyph == "❤️" }).isTrue()
    }

    @Test
    fun `bangladesh flag is searchable`() {
        val realIndex = EmojiSearchIndex(EmojiCatalog.all)
        val results = realIndex.search("bangladesh")
        assertThat(results.map { it.glyph }).contains("🇧🇩")
    }
}
