package com.bornomala.keyboard.suggestions.data.dictionary

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for [FrequencyDictionary] parsing and prefix ranking. */
class FrequencyDictionaryTest {

    private fun dict(vararg lines: String, lowercase: Boolean = true) =
        FrequencyDictionary.build(lines.asSequence(), lowercase)

    @Test
    fun `parses tab separated word and frequency`() {
        val d = dict("the\t100", "to\t50")
        assertThat(d.size).isEqualTo(2)
        assertThat(d.frequencyOf("the")).isEqualTo(100)
        assertThat(d.frequencyOf("to")).isEqualTo(50)
        assertThat(d.maxFrequency()).isEqualTo(100)
    }

    @Test
    fun `ignores comments and blank lines`() {
        val d = dict("# header", "", "the\t100", "   ", "# another")
        assertThat(d.size).isEqualTo(1)
    }

    @Test
    fun `lowercases words when requested`() {
        val d = dict("The\t100", lowercase = true)
        assertThat(d.frequencyOf("the")).isEqualTo(100)
        assertThat(d.frequencyOf("The")).isEqualTo(0)
    }

    @Test
    fun `keeps verbatim case when lowercase disabled`() {
        val d = dict("আমি\t100", lowercase = false)
        assertThat(d.frequencyOf("আমি")).isEqualTo(100)
    }

    @Test
    fun `prefix collection ranks by descending frequency`() {
        val d = dict("the\t100", "they\t80", "their\t90", "to\t50")
        val out = ArrayList<DictionaryHit>()
        val count = d.collectByPrefix("the", limit = 3, out)
        assertThat(count).isEqualTo(3)
        assertThat(out.map { it.word }).containsExactly("the", "their", "they").inOrder()
    }

    @Test
    fun `prefix collection respects the limit keeping highest frequencies`() {
        val d = dict("aa\t10", "ab\t50", "ac\t30", "ad\t40", "ae\t20")
        val out = ArrayList<DictionaryHit>()
        val count = d.collectByPrefix("a", limit = 2, out)
        assertThat(count).isEqualTo(2)
        assertThat(out.map { it.word }).containsExactly("ab", "ad").inOrder()
    }

    @Test
    fun `prefix with no match returns nothing`() {
        val d = dict("the\t100", "to\t50")
        val out = ArrayList<DictionaryHit>()
        val count = d.collectByPrefix("zz", limit = 3, out)
        assertThat(count).isEqualTo(0)
        assertThat(out).isEmpty()
    }

    @Test
    fun `duplicate words keep the highest frequency`() {
        val d = dict("the\t50", "the\t120")
        assertThat(d.frequencyOf("the")).isEqualTo(120)
        assertThat(d.size).isEqualTo(1)
    }

    @Test
    fun `bare word without frequency defaults to one`() {
        val d = dict("solo")
        assertThat(d.frequencyOf("solo")).isEqualTo(1)
    }
}
