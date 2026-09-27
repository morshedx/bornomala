package com.bornomala.keyboard.ime.domain.input

import com.bornomala.keyboard.ime.domain.model.BanglaPhoneticCandidates
import com.bornomala.keyboard.ime.domain.model.BanglaWordMatch

/**
 * Decides which Bangla word space commits in place of the literal transliteration. Pure, so the
 * rules that keep typing predictable are unit-tested directly.
 */
object BanglaAutoPick {

    /** Shortest roman input that may be silently swapped for a phonetic-dictionary word. */
    const val MIN_PHONETIC_PICK_LEN = 3

    /**
     * The word to commit on space, or null to keep exactly what was typed. In order:
     *
     *  1. the user's own earlier pick for this exact spelling — an explicit choice, always honoured;
     *  2. a whole-word [word] match — an English loanword (`chair` -> চেয়ার) or an Avro spelling
     *     fix (`beshI` -> বেশি). When the typed spelling already renders a *trusted* dictionary
     *     word (a frequency-list word) it is applied only if it is the more frequent of the two:
     *     `karon` -> কারণ swaps, but `nice` keeps নিচে and `apon` keeps আপন, with the match
     *     offered one tap away. Weaker evidence does not count:
     *      - a rendering the keyboard merely *learned* — an earlier miss (চম্পুতের committed where
     *        the loanword could not apply) must not block কম্পিউটার forever;
     *      - suggest-only candidates (riti dictionary words, suffix joins) — `chair` renders ছাইর,
     *        which suffix joining also builds as ছাই + র, and must still become চেয়ার.
     *     A user who really wants the rendering taps it once, which records a pick (rule 1);
     *  3. the top *trusted* phonetic-dictionary word, withheld when the roman is still too short to
     *     be a finished word or when the transliteration is itself a known word — learned or not,
     *     since then it is not a misspelling to fix. Suggest-only candidates count as known words
     *     for that check but are never auto-picked.
     */
    fun choose(
        roman: String,
        rendered: String,
        word: BanglaWordMatch?,
        candidates: BanglaPhoneticCandidates,
    ): String? {
        val phonetic = candidates.words
        val renderedRank = if (rendered.isEmpty()) -1 else phonetic.indexOf(rendered)
        val renderedIsKnownWord = renderedRank >= 0
        if (word != null) {
            val renderedIsTrustedWord = renderedRank in 0 until candidates.trustedCount &&
                rendered !in candidates.learnedOnly
            if (word.learned || !renderedIsTrustedWord) return word.word
            // Only the trusted head of the list is frequency-ranked, so only there does an
            // earlier position mean "more common".
            val wordRank = phonetic.indexOf(word.word)
            if (wordRank in 0 until minOf(renderedRank, candidates.trustedCount)) return word.word
        }
        if (roman.length < MIN_PHONETIC_PICK_LEN) return null
        if (renderedIsKnownWord) return null
        return candidates.topTrusted
    }
}
