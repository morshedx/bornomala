package com.bornomala.keyboard.suggestions.domain.model

/**
 * A single ranked candidate offered to the user in the suggestion bar.
 *
 * Immutable and stable so Compose can skip recomposition when the same suggestion
 * reappears. [score] is a normalized ranking weight (higher = more likely); it is
 * an internal ranking detail and the UI should rely on list order, not the value.
 *
 * @property word the candidate text to commit on tap.
 * @property language the language this candidate belongs to.
 * @property source where the candidate originated, used for ranking and debugging.
 * @property score ranking weight; larger is ranked first. Never negative.
 * @property isExactMatch true when [word] equals the user's current input verbatim
 *   (used to surface a verbatim "keep what I typed" option even if it is not in any
 *   dictionary).
 */
data class Suggestion(
    val word: String,
    val language: SuggestionLanguage,
    val source: SuggestionSource,
    val score: Double,
    val isExactMatch: Boolean = false,
)

/** Where a [Suggestion] came from. Drives tie-breaking and future analytics-free debugging. */
enum class SuggestionSource {
    /** Learned from the user's own typing (Room user dictionary). Ranked with a boost. */
    USER_DICTIONARY,

    /** Bundled offline frequency dictionary shipped as an asset. */
    OFFLINE_DICTIONARY,

    /** Verbatim copy of the user's current input. */
    VERBATIM,

    /** Reserved for a future cloud provider. Never produced in V1. */
    CLOUD,
}
