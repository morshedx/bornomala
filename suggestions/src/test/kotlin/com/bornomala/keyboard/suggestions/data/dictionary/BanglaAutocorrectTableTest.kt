package com.bornomala.keyboard.suggestions.data.dictionary

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BanglaAutocorrectTableTest {

    /** Stand-in for the Avro parser: maps the few notations these tests use. */
    private val render: (String) -> String = { avro ->
        when (avro) {
            "ceyar" -> "চেয়ার"
            "bas" -> "বাস"
            "Data" -> "ডাটা"
            "10m" -> "10m"
            else -> "?$avro"
        }
    }

    private fun table(json: List<String>, overlay: List<String> = emptyList()) =
        BanglaAutocorrectTable.build(json.asSequence(), overlay.asSequence(), render)

    @Test
    fun `parses riti json lines and skips structure`() {
        assertThat(BanglaAutocorrectTable.parseJsonEntry("""  "chair": "ceyar",""")).isEqualTo("chair" to "ceyar")
        assertThat(BanglaAutocorrectTable.parseJsonEntry("""  "zugno": "zugmo"""")).isEqualTo("zugno" to "zugmo")
        assertThat(BanglaAutocorrectTable.parseJsonEntry("{")).isNull()
        assertThat(BanglaAutocorrectTable.parseJsonEntry("}")).isNull()
        assertThat(BanglaAutocorrectTable.parseJsonEntry("""  "broken": """)).isNull()
    }

    @Test
    fun `renders avro targets to bangla`() {
        val t = table(listOf("{", """  "chair": "ceyar",""", """  "bus": "bas"""", "}"))
        assertThat(t.lookup("chair")).isEqualTo("চেয়ার")
        assertThat(t.lookup("bus")).isEqualTo("বাস")
        assertThat(t.lookup("table")).isNull()
    }

    @Test
    fun `entries that render without bangla are dropped`() {
        val t = table(listOf("""  "10th": "10m","""))
        assertThat(t.lookup("10th")).isNull()
        assertThat(t.size).isEqualTo(0)
    }

    @Test
    fun `overlay wins over the avro table and skips comments`() {
        val t = table(
            json = listOf("""  "bus": "bas","""),
            overlay = listOf("# header", "", "bus\tবাস্", "chair\tচেয়ার", "junk-without-tab"),
        )
        assertThat(t.lookup("bus")).isEqualTo("বাস্")
        assertThat(t.lookup("chair")).isEqualTo("চেয়ার")
        assertThat(t.size).isEqualTo(2)
    }

    @Test
    fun `lookup is case sensitive first then falls back to lowercase`() {
        val t = table(listOf("""  "Data": "Data",""", """  "chair": "ceyar","""))
        assertThat(t.lookup("Data")).isEqualTo("ডাটা")
        assertThat(t.lookup("data")).isNull()
        assertThat(t.lookup("Chair")).isEqualTo("চেয়ার")
    }
}
