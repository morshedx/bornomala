package com.bornomala.keyboard.transliteration.domain.model

/**
 * Immutable snapshot produced by the [com.bornomala.keyboard.transliteration.domain.engine.TransliterationEngine]
 * after every keystroke or deletion.
 *
 * The engine works on a per-word ("composition") basis. A word is built up in a Latin
 * ASCII buffer; the engine re-derives the Bangla rendering from the whole buffer on each
 * change. This makes backspace correct by construction — it simply trims the buffer and
 * re-derives — and keeps the per-keystroke path allocation-light (a single result object).
 *
 * Marked stable/immutable so Compose can skip recomposition when an identical result is
 * re-emitted (e.g. a key that does not change the composition).
 *
 * @property rawInput the current Latin buffer the user has typed for this word.
 * @property composed the live Bangla rendering of [rawInput], shown inline at the cursor.
 * @property commitCandidate the best text to commit when the word ends (space / punctuation /
 *   focus change). Usually equals [composed], but a phonetic-correction or dictionary layer
 *   may refine it (e.g. resolving ambiguous spellings to the conventional word form).
 * @property isComposing whether there is an active composition (buffer non-empty).
 */
data class TransliterationResult(
    val rawInput: String,
    val composed: String,
    val commitCandidate: String,
    val isComposing: Boolean,
) {
    companion object {
        /** The empty / reset state: nothing being composed. */
        val EMPTY = TransliterationResult(
            rawInput = "",
            composed = "",
            commitCandidate = "",
            isComposing = false,
        )
    }
}
