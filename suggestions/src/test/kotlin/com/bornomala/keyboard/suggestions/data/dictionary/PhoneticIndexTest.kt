package com.bornomala.keyboard.suggestions.data.dictionary

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneticIndexTest {

    private val index = PhoneticIndex.build(
        sequenceOf(
            "# header",
            "",
            "cara\tছাড়া ছাড়াও\tচারা চাড়া",
            "ami\tআমি",
            "abaca\t\tআবাছা",
            "b\tব",
            "ba\tবা বাঃ",
        ),
    )

    @Test
    fun `splits trusted and suggest-only words`() {
        val hits = index.lookup("cara", 10)
        assertThat(hits.trusted).containsExactly("ছাড়া", "ছাড়াও").inOrder()
        assertThat(hits.extra).containsExactly("চারা", "চাড়া").inOrder()
    }

    @Test
    fun `a key may have only suggest-only words`() {
        val hits = index.lookup("abaca", 10)
        assertThat(hits.trusted).isEmpty()
        assertThat(hits.extra).containsExactly("আবাছা")
    }

    @Test
    fun `a two-column line has no suggest-only words`() {
        assertThat(index.lookup("ami", 10)).isEqualTo(PhoneticHits(listOf("আমি"), emptyList()))
    }

    @Test
    fun `unsorted input still resolves every key, including prefixes of each other`() {
        assertThat(index.size).isEqualTo(5)
        assertThat(index.lookup("b", 5).trusted).containsExactly("ব")
        assertThat(index.lookup("ba", 5).trusted).containsExactly("বা", "বাঃ").inOrder()
        assertThat(index.contains("abaca")).isTrue()
    }

    @Test
    fun `missing keys and zero limits return nothing`() {
        assertThat(index.lookup("zzz", 5)).isEqualTo(PhoneticHits.EMPTY)
        assertThat(index.lookup("", 5)).isEqualTo(PhoneticHits.EMPTY)
        assertThat(index.lookup("cara", 0)).isEqualTo(PhoneticHits.EMPTY)
        assertThat(index.contains("car")).isFalse()
    }

    @Test
    fun `limit caps each group`() {
        val hits = index.lookup("cara", 1)
        assertThat(hits.trusted).containsExactly("ছাড়া")
        assertThat(hits.extra).containsExactly("চারা")
    }

    @Test
    fun `empty index finds nothing`() {
        assertThat(PhoneticIndex.EMPTY.lookup("cara", 5)).isEqualTo(PhoneticHits.EMPTY)
        assertThat(PhoneticIndex.build(emptySequence()).size).isEqualTo(0)
    }
}
