package com.bornomala.keyboard.ime.domain.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BanglaAcronymTest {

    @Test
    fun `spells english letters by their bangla names`() {
        assertThat(BanglaAcronym.spell("ips")).isEqualTo("আইপিএস")
        assertThat(BanglaAcronym.spell("SMS")).isEqualTo("এসএমএস")
        assertThat(BanglaAcronym.spell("wy")).isEqualTo("ডব্লিউওয়াই")
        assertThat(BanglaAcronym.spell("hxz")).isEqualTo("এইচএক্সজেড")
    }

    @Test
    fun `only short plain-letter input qualifies`() {
        assertThat(BanglaAcronym.spell("a")).isNull()
        assertThat(BanglaAcronym.spell("school")).isNull()
        assertThat(BanglaAcronym.spell("cha^d")).isNull()
        assertThat(BanglaAcronym.spell("")).isNull()
    }

    @Test
    fun `vowelless means no a e i o u`() {
        assertThat(BanglaAcronym.isVowelless("sms")).isTrue()
        assertThat(BanglaAcronym.isVowelless("PC")).isTrue()
        assertThat(BanglaAcronym.isVowelless("ips")).isFalse()
        assertThat(BanglaAcronym.isVowelless("bUs")).isFalse()
    }
}
