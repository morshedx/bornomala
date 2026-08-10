package com.bornomala.keyboard.ime.domain.input

import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.Suggestion
import com.bornomala.keyboard.ime.domain.port.EditorPort
import com.bornomala.keyboard.ime.domain.port.TransliterationPort
import com.bornomala.keyboard.ime.domain.state.KeyboardStateHolder
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Space-time auto-correction, in both languages, obeys the user's Auto-correction setting.
 *
 * Bangla used to be exempt: the top phonetic-dictionary word was swapped in on space no matter
 * what the setting said, so a bad index hit silently rewrote a correctly typed word.
 */
class InputInteractorAutoCorrectTest {

    private val editor = FakeEditor()
    private val stateHolder = KeyboardStateHolder()
    private val committed = ArrayList<String>()

    private val interactor = InputInteractor(
        editor = editor,
        transliteration = PassThroughTransliteration,
        stateHolder = stateHolder,
        callbacks = object : InputInteractor.Callbacks {
            override fun onWordCommitted(language: KeyboardLanguage, word: String) {
                committed.add(word)
            }

            override fun onComposingChanged(language: KeyboardLanguage, currentWord: String) = Unit
            override fun onEmojiRequested() = Unit
            override fun onShowImePicker() = Unit
            override fun onFeedback(action: KeyAction) = Unit
        },
        clock = { 0L },
    )

    /** Puts the keyboard in the state the strip produces mid-word: composing text + a flagged pick. */
    private fun compose(language: KeyboardLanguage, typed: String, autoCorrectTo: String) {
        stateHolder.setLanguage(language)
        stateHolder.setComposing(typed)
        stateHolder.setSuggestions(
            listOf(
                Suggestion(text = typed),
                Suggestion(text = autoCorrectTo, isAutoCorrect = true),
            ),
        )
        editor.setComposingText(typed)
    }

    @Test
    fun `bangla space keeps the typed word when auto-correction is off`() {
        interactor.updateConfig(InputConfig(autoCorrectEnabled = false))
        compose(KeyboardLanguage.BANGLA, typed = "শশা", autoCorrectTo = "সা")

        interactor.onKey(KeyAction.Space)

        assertThat(editor.text.toString()).isEqualTo("শশা ")
        assertThat(committed).containsExactly("শশা")
    }

    @Test
    fun `bangla space applies the phonetic pick when auto-correction is on`() {
        interactor.updateConfig(InputConfig(autoCorrectEnabled = true))
        compose(KeyboardLanguage.BANGLA, typed = "চারা", autoCorrectTo = "ছাড়া")

        interactor.onKey(KeyAction.Space)

        assertThat(editor.text.toString()).isEqualTo("ছাড়া ")
        assertThat(committed).containsExactly("ছাড়া")
    }

    @Test
    fun `english space keeps the typed word when auto-correction is off`() {
        interactor.updateConfig(InputConfig(autoCorrectEnabled = false))
        compose(KeyboardLanguage.ENGLISH, typed = "teh", autoCorrectTo = "the")

        interactor.onKey(KeyAction.Space)

        assertThat(editor.text.toString()).isEqualTo("teh ")
    }

    @Test
    fun `suggestions off disables auto-correction regardless of the setting`() {
        interactor.updateConfig(InputConfig(autoCorrectEnabled = true, suggestionsEnabled = false))
        compose(KeyboardLanguage.BANGLA, typed = "শশা", autoCorrectTo = "সা")

        interactor.onKey(KeyAction.Space)

        assertThat(editor.text.toString()).isEqualTo("শশা ")
    }

    @Test
    fun `backspace right after an applied correction restores what was typed`() {
        interactor.updateConfig(InputConfig(autoCorrectEnabled = true))
        compose(KeyboardLanguage.BANGLA, typed = "চারা", autoCorrectTo = "ছাড়া")

        interactor.onKey(KeyAction.Space)
        interactor.onKey(KeyAction.Backspace)

        assertThat(editor.text.toString()).isEqualTo("চারা")
    }

    /** Minimal in-memory [EditorPort]: a text buffer with a composing region at its tail. */
    private class FakeEditor : EditorPort {
        val text = StringBuilder()
        private var composingStart = -1

        override fun commitText(text: String) {
            clearComposing()
            this.text.append(text)
        }

        override fun setComposingText(text: String) {
            clearComposing()
            if (text.isEmpty()) return
            composingStart = this.text.length
            this.text.append(text)
        }

        override fun finishComposing() {
            composingStart = -1
        }

        override fun deleteSurroundingText(beforeChars: Int, afterChars: Int) {
            val from = (text.length - beforeChars).coerceAtLeast(0)
            text.delete(from, text.length)
        }

        override fun sendDefaultEditorActionOrNewline() {
            text.append('\n')
        }

        override fun backspace() {
            if (text.isNotEmpty()) text.deleteCharAt(text.length - 1)
        }

        override fun moveCursorBy(chars: Int) = Unit

        override fun textBeforeCursor(n: Int): CharSequence =
            text.substring((text.length - n).coerceAtLeast(0), text.length)

        override fun hasSelection(): Boolean = false

        /** Replacing/finishing a composing region drops the previously composed text. */
        private fun clearComposing() {
            if (composingStart >= 0) {
                text.delete(composingStart, text.length)
                composingStart = -1
            }
        }
    }

    /** The interactor's Bangla path is not exercised here; rendering is identity. */
    private object PassThroughTransliteration : TransliterationPort {
        override fun transliterate(buffer: String): String = buffer
    }
}
