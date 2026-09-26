package com.bornomala.keyboard.ime.domain.model

/**
 * A whole-word Bangla match for the exact roman spelling typed — an English loanword
 * (`software` -> সফটওয়্যার) or the user's own earlier pick for that spelling.
 *
 * @param learned true for the user's own pick: it is auto-picked even when the typed spelling
 *   is itself a real Bangla word, because the user chose it explicitly.
 */
data class BanglaWordMatch(val word: String, val learned: Boolean)
