package com.bornomala.keyboard.suggestions.domain.model

/**
 * A whole-word Bangla match for a roman spelling.
 *
 * @param word the Bangla word.
 * @param learned true when it comes from the user's own earlier pick for this spelling (a
 *   strong signal), false when it comes from the bundled auto-correct table.
 */
data class BanglaWordMatch(val word: String, val learned: Boolean)
