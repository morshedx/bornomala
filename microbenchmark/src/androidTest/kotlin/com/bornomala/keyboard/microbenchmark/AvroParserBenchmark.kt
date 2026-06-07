package com.bornomala.keyboard.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bornomala.keyboard.transliteration.data.engine.AvroParser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Microbenchmarks for the Avro transliteration hot path — the per-keystroke work that must
 * stay well under the 16 ms key-press budget. The rule dictionary is loaded once outside the
 * measured loop; only [AvroParser.parse] is timed.
 *
 * Run on a device:  `./gradlew :microbenchmark:connectedReleaseAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class AvroParserBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val parser = AvroParser.load()

    @Test
    fun parseShortWord() {
        benchmarkRule.measureRepeated {
            parser.parse("ami")
        }
    }

    @Test
    fun parseLongWord() {
        benchmarkRule.measureRepeated {
            parser.parse("bangladesh")
        }
    }

    @Test
    fun parseConjunctWord() {
        benchmarkRule.measureRepeated {
            parser.parse("shikkha")
        }
    }

    @Test
    fun parseFullSentenceWorth() {
        // Simulates re-deriving a long buffer (worst case for the recompose-on-each-key model).
        benchmarkRule.measureRepeated {
            parser.parse("amrasokolebanglaykothabolibphonetictransliteration")
        }
    }
}
