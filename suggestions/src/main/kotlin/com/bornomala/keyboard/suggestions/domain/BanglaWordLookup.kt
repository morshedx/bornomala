package com.bornomala.keyboard.suggestions.domain

import com.bornomala.keyboard.suggestions.domain.model.BanglaWordMatch

/**
 * Whole-word Bangla lookup keyed by the exact roman spelling the user typed, for words that
 * letter-by-letter Avro cannot produce — chiefly English loanwords (`software` -> সফটওয়্যার,
 * `chair` -> চেয়ার) — plus the words the user has explicitly picked for a given spelling.
 *
 * Complements [SuggestionEngine.banglaPhoneticCandidates], which matches by phonetic key and so
 * never reaches a loanword whose English spelling differs from its Bangla pronunciation.
 */
interface BanglaWordLookup {

    /**
     * The Bangla word for [roman], or null when neither a learned pick nor the bundled
     * auto-correct table has one. A learned pick wins over the bundled entry.
     */
    suspend fun lookup(roman: String): BanglaWordMatch?

    /** Remembers that the user chose [word] for [roman], so it becomes the next auto-pick. */
    suspend fun learnPick(roman: String, word: String)
}
