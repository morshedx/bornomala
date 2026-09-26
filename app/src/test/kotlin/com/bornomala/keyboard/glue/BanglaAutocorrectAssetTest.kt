package com.bornomala.keyboard.glue

import com.bornomala.keyboard.suggestions.data.dictionary.BanglaAutocorrectTable
import com.bornomala.keyboard.transliteration.data.engine.AvroParser
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Builds the shipped auto-correct table exactly as the app does — riti's Avro-notation JSON
 * rendered through the real [AvroParser], then Bornomala's loanword overlay — and checks that
 * common English loanwords come out as their Bangla spellings.
 */
class BanglaAutocorrectAssetTest {

    private val table: BanglaAutocorrectTable by lazy {
        val parser = AvroParser.load()
        BanglaAutocorrectTable.build(
            avroJsonLines = File(ASSET_DIR, "avro_autocorrect.json").readLines().asSequence(),
            overlayLines = File(ASSET_DIR, "bn_loanwords.txt").readLines().asSequence(),
            render = parser::parse,
        )
    }

    @Test
    fun `common loanwords resolve to their bangla spelling`() {
        val expected = mapOf(
            "software" to "সফটওয়্যার",
            "chair" to "চেয়ার",
            "keyboard" to "কিবোর্ড",
            "mouse" to "মাউস",
            "computer" to "কম্পিউটার",
            "facebook" to "ফেসবুক",
            "screen" to "স্ক্রিন",
            "bus" to "বাস",
            "Software" to "সফটওয়্যার",
        )
        expected.forEach { (roman, bangla) ->
            assertWithMessage(roman).that(table.lookup(roman)).isEqualTo(bangla)
        }
    }

    @Test
    fun `the riti table loads thousands of entries`() {
        assertThat(table.size).isGreaterThan(4_000)
    }

    @Test
    fun `overlay uses precomposed nukta letters like the other dictionaries`() {
        val overlay = File(ASSET_DIR, "bn_loanwords.txt").readText()
        assertThat(overlay).doesNotContain("\u09AF\u09BC")
        assertThat(overlay).doesNotContain("\u09A1\u09BC")
        assertThat(overlay).doesNotContain("\u09A2\u09BC")
    }

    private companion object {
        const val ASSET_DIR = "../suggestions/src/main/assets/dictionaries"
    }
}
