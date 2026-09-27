package com.bornomala.keyboard.ime.domain.input

import com.bornomala.keyboard.ime.domain.model.BanglaPhoneticCandidates
import com.bornomala.keyboard.ime.domain.model.BanglaWordMatch
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BanglaAutoPickTest {

    private fun candidates(vararg words: String, trusted: Int = words.size, learnedOnly: Set<String> = emptySet()) =
        BanglaPhoneticCandidates(words.toList(), trusted, learnedOnly)

    private val loanword = BanglaWordMatch("কম্পিউটার", learned = false)

    @Test
    fun `a loanword replaces a rendering that is no known word`() {
        assertThat(BanglaAutoPick.choose("computer", "চম্পুতের", loanword, candidates()))
            .isEqualTo("কম্পিউটার")
    }

    /**
     * Regression (0.9.2): চম্পুতের, committed where the loanword could not apply, was learned — and
     * from then on counted as a real word, blocking কম্পিউটার in every field.
     */
    @Test
    fun `a merely learned rendering does not block a loanword`() {
        val learned = candidates("চম্পুতের", trusted = 1, learnedOnly = setOf("চম্পুতের"))
        assertThat(BanglaAutoPick.choose("computer", "চম্পুতের", loanword, learned)).isEqualTo("কম্পিউটার")
    }

    /**
     * Regression (0.9.3): `chair` renders ছাইর, which suffix joining also builds (ছাই + র) as a
     * suggest-only candidate; that blocked চেয়ার. Same for riti-only words (back -> বাচক).
     */
    @Test
    fun `suggest-only candidates do not block a loanword`() {
        val chair = BanglaWordMatch("চেয়ার", learned = false)
        val suffixJoin = candidates("চাইর", "ছাইর", trusted = 0)
        assertThat(BanglaAutoPick.choose("chair", "ছাইর", chair, suffixJoin)).isEqualTo("চেয়ার")

        val back = BanglaWordMatch("ব্যাক", learned = false)
        assertThat(BanglaAutoPick.choose("back", "বাচক", back, candidates("বাচক", trusted = 0))).isEqualTo("ব্যাক")
    }

    @Test
    fun `a suggest-only rendering still blocks an ordinary phonetic swap`() {
        assertThat(BanglaAutoPick.choose("chair", "ছাইর", null, candidates("চাইর", "ছাইর", trusted = 1))).isNull()
    }

    @Test
    fun `a rarer loanword does not replace a dictionary word`() {
        val nice = BanglaWordMatch("নাইস", learned = false)
        assertThat(BanglaAutoPick.choose("nice", "নিচে", nice, candidates("নিচে"))).isNull()
    }

    @Test
    fun `a more frequent spelling fix replaces a dictionary misspelling`() {
        val fix = BanglaWordMatch("কারণ", learned = false)
        assertThat(BanglaAutoPick.choose("karon", "কারন", fix, candidates("কারণ", "কারন"))).isEqualTo("কারণ")
    }

    @Test
    fun `the user's own pick always wins`() {
        val pick = BanglaWordMatch("চম্পুতের", learned = true)
        assertThat(BanglaAutoPick.choose("computer", "চম্পুতের", pick, candidates("চম্পুতের"))).isEqualTo("চম্পুতের")
    }

    @Test
    fun `dictionary words still stop an ordinary phonetic swap`() {
        assertThat(BanglaAutoPick.choose("shosha", "শশা", null, candidates("শশা", "সা"))).isNull()
    }

    /** Regression: বেতারি, learned from an earlier miss, kept `betari` from becoming ব্যাটারি. */
    @Test
    fun `a merely learned rendering does not stop a phonetic swap`() {
        val learned = candidates("বেতারি", "ব্যাটারি", trusted = 2, learnedOnly = setOf("বেতারি"))
        assertThat(BanglaAutoPick.choose("betari", "বেতারি", null, learned)).isEqualTo("ব্যাটারি")
    }

    @Test
    fun `vowelless abbreviations are spelled out on space`() {
        assertThat(BanglaAutoPick.choose("sms", "স্ম্স", null, candidates(), BanglaAcronym.spell("sms")))
            .isEqualTo("এসএমএস")
        assertThat(BanglaAutoPick.choose("kg", "ক্গ", null, candidates(), BanglaAcronym.spell("kg")))
            .isEqualTo("কেজি")
    }

    @Test
    fun `abbreviations with vowels are only offered, never swapped in`() {
        assertThat(BanglaAutoPick.choose("ips", "ইপ্স", null, candidates(), BanglaAcronym.spell("ips"))).isNull()
        assertThat(BanglaAutoPick.choose("rif", "রিফ", null, candidates(), BanglaAcronym.spell("rif"))).isNull()
    }

    @Test
    fun `a loanword or pick beats the spelled-out form`() {
        val km = BanglaWordMatch("কিমি.", learned = false)
        assertThat(BanglaAutoPick.choose("km", "ক্ম", km, candidates(), BanglaAcronym.spell("km"))).isEqualTo("কিমি.")
        val pick = BanglaWordMatch("আইপিএস", learned = true)
        assertThat(BanglaAutoPick.choose("ips", "ইপ্স", pick, candidates(), BanglaAcronym.spell("ips"))).isEqualTo("আইপিএস")
    }

    @Test
    fun `a trusted rendering is not spelled out`() {
        assertThat(BanglaAutoPick.choose("hm", "হ্ম", null, candidates("হ্ম", trusted = 1), BanglaAcronym.spell("hm"))).isNull()
    }

    @Test
    fun `phonetic swaps need a trusted word and a finished-looking word`() {
        assertThat(BanglaAutoPick.choose("chara", "চারা", null, candidates("ছাড়া", trusted = 1))).isEqualTo("ছাড়া")
        assertThat(BanglaAutoPick.choose("chara", "চারা", null, candidates("ছাড়া", trusted = 0))).isNull()
        assertThat(BanglaAutoPick.choose("ch", "চ", null, candidates("ছ"))).isNull()
    }
}
