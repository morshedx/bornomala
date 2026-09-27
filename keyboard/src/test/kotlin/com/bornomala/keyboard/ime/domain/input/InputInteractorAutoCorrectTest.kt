package com.bornomala.keyboard.ime.domain.input

import com.bornomala.keyboard.ime.domain.model.CapsMode
import com.bornomala.keyboard.ime.domain.model.FieldProfile
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.ShiftState
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
    private val picks = ArrayList<Pair<String, String>>()

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
            override fun onBanglaPicked(roman: String, word: String) {
                picks.add(roman to word)
            }
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

    private fun typeBangla(roman: String) {
        stateHolder.setLanguage(KeyboardLanguage.BANGLA)
        roman.forEach { interactor.onKey(KeyAction.Character(it)) }
    }

    @Test
    fun `password fields never compose, auto-correct or learn from the strip`() {
        interactor.updateConfig(InputConfig(autoCorrectEnabled = true))
        interactor.setField(FieldProfile(isPassword = true, allowLearning = false, allowSuggestions = false, allowAutoCorrect = false, capsMode = CapsMode.NONE))
        stateHolder.setLanguage(KeyboardLanguage.ENGLISH)
        "teh".forEach { interactor.onKey(KeyAction.Character(it)) }

        assertThat(stateHolder.current.isComposing).isFalse()
        interactor.onKey(KeyAction.Space)
        assertThat(editor.text.toString()).isEqualTo("teh ")
    }

    @Test
    fun `fields without auto-correct keep what was typed`() {
        interactor.updateConfig(InputConfig(autoCorrectEnabled = true))
        interactor.setField(FieldProfile(allowAutoCorrect = false))
        compose(KeyboardLanguage.ENGLISH, typed = "teh", autoCorrectTo = "the")

        interactor.onKey(KeyAction.Space)

        assertThat(editor.text.toString()).isEqualTo("teh ")
    }

    /** Regression (0.9.2): typing `computer` in Chrome's address bar committed চম্পুতের. */
    @Test
    fun `bangla auto-pick still applies in fields that forbid english auto-correct`() {
        interactor.updateConfig(InputConfig(autoCorrectEnabled = true))
        interactor.setField(FieldProfile(allowAutoCorrect = false))
        compose(KeyboardLanguage.BANGLA, typed = "চম্পুতের", autoCorrectTo = "কম্পিউটার")

        interactor.onKey(KeyAction.Space)

        assertThat(editor.text.toString()).isEqualTo("কম্পিউটার ")
    }

    @Test
    fun `bangla auto-pick still obeys the user's auto-correction setting there`() {
        interactor.updateConfig(InputConfig(autoCorrectEnabled = false))
        interactor.setField(FieldProfile(allowAutoCorrect = false))
        compose(KeyboardLanguage.BANGLA, typed = "চম্পুতের", autoCorrectTo = "কম্পিউটার")

        interactor.onKey(KeyAction.Space)

        assertThat(editor.text.toString()).isEqualTo("চম্পুতের ")
    }

    @Test
    fun `capitalization follows the field`() {
        interactor.updateConfig(InputConfig(autoCapitalization = true))
        stateHolder.setLanguage(KeyboardLanguage.ENGLISH)

        interactor.setField(FieldProfile(capsMode = CapsMode.NONE))
        interactor.refreshAutoCapitalization()
        assertThat(stateHolder.current.shift).isEqualTo(ShiftState.OFF)

        interactor.setField(FieldProfile(capsMode = CapsMode.SENTENCES))
        interactor.refreshAutoCapitalization()
        assertThat(stateHolder.current.shift).isEqualTo(ShiftState.SHIFTED)

        interactor.setField(FieldProfile(capsMode = CapsMode.CHARACTERS, allowSuggestions = false))
        interactor.onKey(KeyAction.Character('a'))
        assertThat(editor.text.toString()).isEqualTo("A")
        assertThat(stateHolder.current.shift).isEqualTo(ShiftState.SHIFTED)

        interactor.setField(FieldProfile(capsMode = CapsMode.WORDS, allowSuggestions = false))
        interactor.onKey(KeyAction.Character('b'))
        assertThat(stateHolder.current.shift).isEqualTo(ShiftState.OFF)
        interactor.onKey(KeyAction.Space)
        assertThat(stateHolder.current.shift).isEqualTo(ShiftState.SHIFTED)
    }

    @Test
    fun `double comma in a bangla word is avro's explicit hasant`() {
        typeBangla("k,,Sh")

        assertThat(stateHolder.current.composingText).isEqualTo("k,,Sh")
        assertThat(committed).isEmpty()
    }

    @Test
    fun `a single comma after a bangla word stays an ordinary comma`() {
        typeBangla("ami,")
        assertThat(editor.text.toString()).isEqualTo("ami,")

        interactor.onKey(KeyAction.Space)

        assertThat(editor.text.toString()).isEqualTo("ami, ")
        assertThat(committed).containsExactly("ami")
    }

    @Test
    fun `a letter after a single comma starts a new word`() {
        typeBangla("ami,t")

        assertThat(committed).containsExactly("ami")
        assertThat(stateHolder.current.composingText).isEqualTo("t")
        assertThat(editor.text.toString()).isEqualTo("ami,t")
    }

    @Test
    fun `backspace removes only the held comma`() {
        typeBangla("ami,")

        interactor.onKey(KeyAction.Backspace)

        assertThat(editor.text.toString()).isEqualTo("ami")
        assertThat(stateHolder.current.composingText).isEqualTo("ami")
        assertThat(committed).isEmpty()
    }

    @Test
    fun `a held comma survives a suggestion tap and an auto-correction`() {
        typeBangla("ami,")
        interactor.commitSuggestion("আমি")
        assertThat(editor.text.toString()).isEqualTo("আমি, ")

        editor.text.setLength(0)
        interactor.updateConfig(InputConfig(autoCorrectEnabled = true))
        typeBangla("chara")
        stateHolder.setSuggestions(listOf(Suggestion(text = "ছাড়া", isAutoCorrect = true)))
        interactor.onKey(KeyAction.Character(','))
        interactor.onKey(KeyAction.Space)
        assertThat(editor.text.toString()).isEqualTo("ছাড়া, ")
    }

    @Test
    fun `english commas are unaffected`() {
        stateHolder.setLanguage(KeyboardLanguage.ENGLISH)
        "hi,".forEach { interactor.onKey(KeyAction.Character(it)) }

        assertThat(editor.text.toString()).isEqualTo("hi,")
        assertThat(stateHolder.current.isComposing).isFalse()
    }

    @Test
    fun `avro symbols inside a bangla word join the roman buffer`() {
        typeBangla("cha^d")
        assertThat(stateHolder.current.composingText).isEqualTo("cha^d")

        stateHolder.setComposing("")
        interactor.resetComposing()
        typeBangla("du:kho")
        assertThat(stateHolder.current.composingText).isEqualTo("du:kho")
    }

    @Test
    fun `avro symbols typed before any letter stay literal`() {
        typeBangla(":")
        assertThat(stateHolder.current.isComposing).isFalse()
        assertThat(editor.text.toString()).isEqualTo(":")
    }

    @Test
    fun `tapping a bangla suggestion remembers it for the typed roman`() {
        typeBangla("bus")

        interactor.commitSuggestion("বাস")

        assertThat(picks).containsExactly("bus" to "বাস")
        assertThat(editor.text.toString()).isEqualTo("বাস ")
    }

    @Test
    fun `tapping the raw latin chip is not remembered as a bangla pick`() {
        typeBangla("bus")

        interactor.commitSuggestion("bus")

        assertThat(picks).isEmpty()
    }

    @Test
    fun `english suggestion taps are not bangla picks`() {
        stateHolder.setLanguage(KeyboardLanguage.ENGLISH)
        "teh".forEach { interactor.onKey(KeyAction.Character(it)) }

        interactor.commitSuggestion("the")

        assertThat(picks).isEmpty()
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
