package com.bornomala.keyboard.transliteration

import com.bornomala.keyboard.transliteration.data.engine.AvroParser
import com.bornomala.keyboard.transliteration.data.engine.AvroTransliterationEngine
import com.bornomala.keyboard.transliteration.domain.engine.TransliterationEngine
import com.bornomala.keyboard.transliteration.domain.model.TransliterationResult
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Table-driven tests for [AvroTransliterationEngine] over the official OmicronLab Avro
 * dictionary. Expected values are the authentic Avro Phonetic outputs (independently verified
 * against the reference algorithm), not idealized spellings — e.g. lowercase `o` is the
 * inherent vowel, so `valo` -> ভাল and `shob` -> শব (type `bhalo` / `sob` for ভালো / সব).
 *
 * Each case types the Latin string one character at a time (mirroring real keystrokes) and
 * asserts the committed candidate, exercising the recompose-on-every-keystroke path.
 */
@RunWith(Parameterized::class)
class AvroTransliterationEngineTest(
    private val case: Case,
) {

    data class Case(val input: String, val expected: String) {
        override fun toString(): String = "$input -> $expected"
    }

    private lateinit var engine: TransliterationEngine

    @Before
    fun setUp() {
        engine = AvroTransliterationEngine(AvroParser.load())
    }

    @Test
    fun typesCharByChar_producesExpectedCommitCandidate() {
        var result = TransliterationResult.EMPTY
        for (ch in case.input) {
            result = engine.processInput(ch.toString())
        }
        assertThat(result.commitCandidate).isEqualTo(case.expected)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Case> = listOf(
            // ---- Common words ----
            Case("ami", "আমি"),
            Case("bangladesh", "বাংলাদেশ"),
            Case("kemon", "কেমন"),
            Case("khub", "খুব"),
            Case("shikkha", "শিক্ষা"),
            Case("sob", "সব"),
            // Uppercase `O` is the ও-kar; lowercase `o` is the inherent vowel.
            Case("valO", "ভালো"),

            // ---- Inherent-vowel `o` behaviour (authentic Avro) ----
            Case("valo", "ভাল"),
            Case("shob", "শব"),
            Case("ko", "ক"),

            // ---- Independent vowels at word start ----
            Case("a", "আ"),
            Case("i", "ই"),
            Case("u", "উ"),
            Case("e", "এ"),
            Case("o", "অ"),

            // ---- Single consonants carry inherent vowel ----
            Case("k", "ক"),
            Case("kh", "খ"),
            Case("g", "গ"),
            Case("gh", "ঘ"),
            Case("sh", "শ"),

            // ---- Dependent vowel signs (matra) after consonant ----
            Case("ka", "কা"),
            Case("ki", "কি"),
            Case("ku", "কু"),
            Case("ke", "কে"),

            // ---- Conjuncts ----
            Case("kkh", "ক্ষ"),
            Case("tt", "ত্ত"),
            Case("dd", "দ্দ"),

            // ---- Anusvara ----
            Case("rong", "রং"),
        )
    }
}
