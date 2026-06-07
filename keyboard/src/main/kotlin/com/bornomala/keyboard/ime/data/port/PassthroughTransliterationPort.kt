package com.bornomala.keyboard.ime.data.port

import com.bornomala.keyboard.ime.domain.port.TransliterationPort
import javax.inject.Inject

/**
 * Safe fallback [TransliterationPort] that performs no transliteration (returns the latin
 * buffer unchanged). The app binds the real adapter over the :transliteration module's
 * engine; this exists so the keyboard graph is complete and functional even before that
 * module is wired, and so isolated builds/tests of :keyboard never NPE.
 *
 * It is intentionally allocation-free on the hot path: it returns the input string
 * directly and an empty candidate list.
 */
class PassthroughTransliterationPort @Inject constructor() : TransliterationPort {
    override fun transliterate(buffer: String): String = buffer
    override fun candidates(buffer: String): List<String> = emptyList()
}
