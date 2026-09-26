package com.bornomala.keyboard.suggestions.data

import com.bornomala.keyboard.core.result.getOrNull
import com.bornomala.keyboard.suggestions.data.dictionary.BanglaAutocorrectRepository
import com.bornomala.keyboard.suggestions.data.dictionary.BanglaSuffixRepository
import com.bornomala.keyboard.suggestions.data.dictionary.BanglaSuffixes
import com.bornomala.keyboard.suggestions.data.local.RomanPickRepository
import com.bornomala.keyboard.suggestions.domain.BanglaWordLookup
import com.bornomala.keyboard.suggestions.domain.model.BanglaWordMatch
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [BanglaWordLookup]. In order:
 *
 *  1. the user's own pick for the exact spelling;
 *  2. the user's pick for a base, carried through a suffix — picking বাস for `bus` makes `buser`
 *     বাসের (as riti's candidate-selection memory does);
 *  3. the bundled auto-correct entry for the exact spelling;
 *  4. a bundled entry for a base, carried through a suffix — `computerer` -> কম্পিউটারের.
 *
 * 1–2 are learned (always honoured); 3–4 are bundled (subject to the keyboard's real-word guard).
 */
@Singleton
class DefaultBanglaWordLookup @Inject constructor(
    private val picks: RomanPickRepository,
    private val autocorrect: BanglaAutocorrectRepository,
    private val suffixes: BanglaSuffixRepository,
) : BanglaWordLookup {

    override suspend fun lookup(roman: String): BanglaWordMatch? {
        if (roman.isBlank()) return null
        pick(roman)?.let { return BanglaWordMatch(it, learned = true) }
        val splits = suffixes.suffixes().splits(roman, MIN_SUFFIXED_BASE)
        for (split in splits) {
            val base = pick(split.base) ?: continue
            return BanglaWordMatch(BanglaSuffixes.join(base, split.suffix), learned = true)
        }
        autocorrect.lookup(roman)?.let { return BanglaWordMatch(it, learned = false) }
        for (split in splits) {
            val base = autocorrect.lookup(split.base) ?: continue
            return BanglaWordMatch(BanglaSuffixes.join(base, split.suffix), learned = false)
        }
        return null
    }

    override suspend fun learnPick(roman: String, word: String) {
        if (roman.isBlank() || word.isBlank()) return
        picks.record(roman, word, SuggestionLanguage.BANGLA)
    }

    private suspend fun pick(roman: String): String? =
        picks.find(roman, SuggestionLanguage.BANGLA).getOrNull()

    private companion object {
        /**
         * Shortest base carried through a suffix. A whole-word match may be swapped in on space,
         * so the base must be a deliberate word on its own (`bus` in `buser`), not a two-letter
         * fragment that happens to be an entry.
         */
        const val MIN_SUFFIXED_BASE = 3
    }
}
