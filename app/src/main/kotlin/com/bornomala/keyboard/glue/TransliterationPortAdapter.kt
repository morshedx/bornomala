package com.bornomala.keyboard.glue

import com.bornomala.keyboard.ime.domain.port.TransliterationPort
import com.bornomala.keyboard.transliteration.domain.engine.TransliterationEngineFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts the :transliteration module's stateful engine onto the keyboard's [TransliterationPort].
 *
 * The keyboard already owns the per-word Latin buffer and hands the whole buffer to
 * [transliterate] on each keystroke, so this adapter drives the engine statelessly: reset,
 * feed the full buffer, read the rendering. Re-deriving from the whole (single-word) buffer
 * keeps matra/conjunct rendering correct and stays well within the per-keystroke budget.
 *
 * One engine instance is created via the factory and reused; it is not thread safe, which is
 * fine because the IME calls the port only from its single input thread.
 */
@Singleton
class TransliterationPortAdapter @Inject constructor(
    factory: TransliterationEngineFactory,
) : TransliterationPort {

    private val engine = factory.create()

    override fun transliterate(buffer: String): String {
        engine.reset()
        if (buffer.isEmpty()) return ""
        return engine.processInput(buffer).composed
    }

    override fun candidates(buffer: String): List<String> {
        engine.reset()
        if (buffer.isEmpty()) return emptyList()
        val result = engine.processInput(buffer)
        // Surface the commit candidate when it differs from the live rendering.
        return if (result.commitCandidate.isNotEmpty() && result.commitCandidate != result.composed) {
            listOf(result.commitCandidate)
        } else {
            emptyList()
        }
    }
}
