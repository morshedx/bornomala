package com.bornomala.keyboard.transliteration

import com.bornomala.keyboard.transliteration.data.engine.AvroParser
import com.bornomala.keyboard.transliteration.data.engine.AvroTransliterationEngine
import com.bornomala.keyboard.transliteration.domain.engine.TransliterationEngine
import com.bornomala.keyboard.transliteration.domain.model.TransliterationResult
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Behavioural tests for composing state, backspace and reset on [AvroTransliterationEngine].
 */
class AvroEngineBehaviorTest {

    private lateinit var engine: TransliterationEngine

    @Before
    fun setUp() {
        engine = AvroTransliterationEngine(AvroParser.load())
    }

    private fun type(text: String): TransliterationResult {
        var r = TransliterationResult.EMPTY
        for (ch in text) r = engine.processInput(ch.toString())
        return r
    }

    @Test
    fun freshEngine_isNotComposing() {
        val r = engine.processInput("")
        assertThat(r).isEqualTo(TransliterationResult.EMPTY)
        assertThat(r.isComposing).isFalse()
    }

    @Test
    fun composing_reportsRawAndComposed() {
        val r = type("ami")
        assertThat(r.isComposing).isTrue()
        assertThat(r.rawInput).isEqualTo("ami")
        assertThat(r.composed).isEqualTo("আমি")
        assertThat(r.commitCandidate).isEqualTo("আমি")
    }

    @Test
    fun backspace_reDerivesFromBuffer() {
        type("ami")
        // remove 'i' -> buffer "am" -> আম
        val afterOne = engine.delete()
        assertThat(afterOne.rawInput).isEqualTo("am")
        assertThat(afterOne.composed).isEqualTo("আম")

        // remove 'm' -> buffer "a" -> আ
        val afterTwo = engine.delete()
        assertThat(afterTwo.rawInput).isEqualTo("a")
        assertThat(afterTwo.composed).isEqualTo("আ")
    }

    @Test
    fun backspace_intoConjunct_recomposesCorrectly() {
        // shikkha -> শিক্ষা ; deleting the trailing 'a' leaves the bare conjunct ক্ষ
        type("shikkha")
        val r = engine.delete() // buffer "shikkh"
        assertThat(r.rawInput).isEqualTo("shikkh")
        assertThat(r.composed).isEqualTo("শিক্ষ")
    }

    @Test
    fun backspace_onEmptyBuffer_returnsEmpty() {
        val r = engine.delete()
        assertThat(r).isEqualTo(TransliterationResult.EMPTY)
    }

    @Test
    fun backspaceToEmpty_clearsComposition() {
        type("ka")
        engine.delete() // "k"
        val r = engine.delete() // ""
        assertThat(r).isEqualTo(TransliterationResult.EMPTY)
        assertThat(r.isComposing).isFalse()
    }

    @Test
    fun reset_clearsAllState() {
        type("bangladesh")
        engine.reset()
        val r = engine.processInput("k")
        assertThat(r.rawInput).isEqualTo("k")
        assertThat(r.composed).isEqualTo("ক")
    }

    @Test
    fun multiCharInput_processedLeftToRight() {
        val r = engine.processInput("ami")
        assertThat(r.composed).isEqualTo("আমি")
    }

    @Test
    fun contextualO_inherentVowel() {
        // Lowercase `o` is the inherent vowel: silent between consonants, and not an ও-kar
        // at the end either — authentic Avro (type `bhalo` for ভালো).
        assertThat(type("kemon").composed).isEqualTo("কেমন")
        engine.reset()
        assertThat(type("valo").composed).isEqualTo("ভাল")
    }

    @Test
    fun commitCandidate_equalsComposed() {
        val r = type("shob")
        // No word-correction layer: the commit candidate is the faithful Avro output.
        assertThat(r.composed).isEqualTo("শব")
        assertThat(r.commitCandidate).isEqualTo("শব")
    }
}
