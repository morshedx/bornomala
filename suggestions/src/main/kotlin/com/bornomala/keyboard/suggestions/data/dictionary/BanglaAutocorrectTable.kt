package com.bornomala.keyboard.suggestions.data.dictionary

/**
 * Immutable roman -> Bangla table built from the bundled auto-correct sources:
 *
 *  1. `avro_autocorrect.json` — OpenBangla riti's auto-correct dictionary (MPL-2.0), shipped
 *     verbatim. Its targets are Avro notation and are rendered to Bangla at build time.
 *  2. `bn_loanwords.txt` — Bornomala's own `roman<TAB>Bangla` overlay. Applied last, so it wins
 *     where both define the same key.
 *
 * Keys are case-sensitive, as in Avro (`Data` and `data` can differ); [lookup] falls back to
 * the lowercased input so a capitalised English word still resolves.
 */
class BanglaAutocorrectTable private constructor(private val entries: Map<String, String>) {

    val size: Int get() = entries.size

    fun lookup(roman: String): String? {
        if (roman.isEmpty()) return null
        entries[roman]?.let { return it }
        val lower = roman.lowercase()
        return if (lower != roman) entries[lower] else null
    }

    companion object {
        val EMPTY = BanglaAutocorrectTable(emptyMap())

        /**
         * Builds the table. [avroJsonLines] are the lines of riti's flat `{"key": "value", …}`
         * JSON; each value goes through [render]. Entries whose rendering contains no Bangla
         * (emoticons, `10th`-style ordinals) are skipped — they are not words to swap in.
         */
        fun build(
            avroJsonLines: Sequence<String>,
            overlayLines: Sequence<String>,
            render: (String) -> String,
        ): BanglaAutocorrectTable {
            val map = HashMap<String, String>(OVERLAY_CAPACITY_HINT)
            for (line in avroJsonLines) {
                val entry = parseJsonEntry(line) ?: continue
                val bangla = render(entry.second)
                if (containsBangla(bangla)) map[entry.first] = bangla
            }
            for (line in overlayLines) {
                if (line.isBlank() || line.startsWith('#')) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val key = line.substring(0, tab).trim()
                val value = line.substring(tab + 1).trim()
                if (key.isNotEmpty() && containsBangla(value)) map[key] = value
            }
            return BanglaAutocorrectTable(map)
        }

        /**
         * Parses one `  "key": "value",` line of a flat JSON object. riti's file is one entry per
         * line with no escapes; anything else (braces, malformed lines) yields null.
         */
        internal fun parseJsonEntry(line: String): Pair<String, String>? {
            val s = line.trim().removeSuffix(",")
            if (!s.startsWith('"') || !s.endsWith('"')) return null
            val sep = s.indexOf("\": \"")
            if (sep <= 1) return null
            val key = s.substring(1, sep)
            val value = s.substring(sep + 4, s.length - 1)
            if (key.contains('"') || value.contains('"') || value.isEmpty()) return null
            return key to value
        }

        private fun containsBangla(s: String): Boolean = s.any { it in 'ঀ'..'৿' }

        private const val OVERLAY_CAPACITY_HINT = 8_192
    }
}
