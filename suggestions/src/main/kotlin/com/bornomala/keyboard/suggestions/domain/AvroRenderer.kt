package com.bornomala.keyboard.suggestions.domain

/**
 * Renders Avro Phonetic roman notation (e.g. `ceyar`) into Bangla (চেয়ার).
 *
 * The bundled auto-correct table stores its targets in Avro notation, exactly as upstream
 * (OpenBangla riti) ships it, so they must pass through the same parser the keyboard types
 * with. `:suggestions` does not depend on `:transliteration`; the app binds this seam to the
 * shared Avro parser. Implementations must be pure and thread-safe.
 */
fun interface AvroRenderer {
    fun render(avro: String): String
}
