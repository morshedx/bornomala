package com.bornomala.keyboard.suggestions.data.dictionary

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BanglaSuffixesTest {

    private val suffixes = BanglaSuffixes.build(
        sequenceOf(
            "{",
            """  "e": "ে",""",
            """  "er": "ের",""",
            """  "te": "তে",""",
            """  "gulo": "গুলো"""",
            "}",
        ),
    )

    @Test
    fun `vowel ending before a vowel sign takes ya`() {
        assertThat(BanglaSuffixes.join("বই", "ে")).isEqualTo("বইয়ে")
        assertThat(BanglaSuffixes.join("বাড়ি", "ের")).isEqualTo("বাড়িয়ের")
    }

    @Test
    fun `consonant-initial suffix joins directly`() {
        assertThat(BanglaSuffixes.join("বাড়ি", "তে")).isEqualTo("বাড়িতে")
        assertThat(BanglaSuffixes.join("বই", "গুলো")).isEqualTo("বইগুলো")
    }

    @Test
    fun `khanda ta and anusvara change before a suffix`() {
        assertThat(BanglaSuffixes.join("জগৎ", "ের")).isEqualTo("জগতের")
        assertThat(BanglaSuffixes.join("রং", "ের")).isEqualTo("রঙের")
    }

    @Test
    fun `splits list every base-plus-suffix reading, longest base first`() {
        assertThat(suffixes.splits("barite")).containsExactly(
            BanglaSuffixes.Split("barit", "ে"),
            BanglaSuffixes.Split("bari", "তে"),
        ).inOrder()
        assertThat(suffixes.splits("boigulo")).containsExactly(BanglaSuffixes.Split("boi", "গুলো"))
    }

    @Test
    fun `splits need a base of at least the minimum length`() {
        assertThat(suffixes.splits("te")).isEmpty()
        assertThat(suffixes.splits("ate")).containsExactly(BanglaSuffixes.Split("at", "ে"))
        assertThat(suffixes.splits("ate", minBase = 3)).isEmpty()
        assertThat(suffixes.splits("xyz")).isEmpty()
    }
}
