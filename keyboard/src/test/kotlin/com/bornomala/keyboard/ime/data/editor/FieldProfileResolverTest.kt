package com.bornomala.keyboard.ime.data.editor

import android.text.InputType.TYPE_CLASS_NUMBER
import android.text.InputType.TYPE_CLASS_PHONE
import android.text.InputType.TYPE_CLASS_TEXT
import android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
import android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
import android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
import android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
import android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
import android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
import android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
import android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
import android.text.InputType.TYPE_TEXT_VARIATION_URI
import android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
import android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
import android.view.inputmethod.EditorInfo
import com.bornomala.keyboard.ime.domain.model.CapsMode
import com.bornomala.keyboard.ime.domain.model.FieldKind
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FieldProfileResolverTest {

    private fun resolve(
        inputType: Int,
        imeOptions: Int = 0,
        hints: List<String> = emptyList(),
        label: String? = null,
    ) = FieldProfileResolver.resolve(inputType, imeOptions, hints, label)

    @Test
    fun `every password variation disables learning, suggestions and auto-correct`() {
        val passwords = listOf(
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD,
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_WEB_PASSWORD,
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            TYPE_CLASS_NUMBER or TYPE_NUMBER_VARIATION_PASSWORD,
        )
        for (type in passwords) {
            val p = resolve(type)
            assertThat(p.isPassword).isTrue()
            assertThat(p.allowLearning).isFalse()
            assertThat(p.allowSuggestions).isFalse()
            assertThat(p.allowAutoCorrect).isFalse()
            assertThat(p.capsMode).isEqualTo(CapsMode.NONE)
            assertThat(p.languageOverride).isEqualTo(KeyboardLanguage.ENGLISH)
        }
    }

    @Test
    fun `incognito fields suggest but never learn`() {
        val p = resolve(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertThat(p.allowLearning).isFalse()
        assertThat(p.allowSuggestions).isTrue()
        assertThat(p.isPassword).isFalse()
    }

    @Test
    fun `no-suggestions flag turns off suggestions and auto-correct`() {
        val p = resolve(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertThat(p.allowSuggestions).isFalse()
        assertThat(p.allowAutoCorrect).isFalse()
        assertThat(p.allowLearning).isTrue()
    }

    @Test
    fun `plain text follows the field's capitalization flags`() {
        assertThat(resolve(TYPE_CLASS_TEXT).capsMode).isEqualTo(CapsMode.NONE)
        assertThat(resolve(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_SENTENCES or TYPE_TEXT_FLAG_MULTI_LINE).capsMode)
            .isEqualTo(CapsMode.SENTENCES)
        assertThat(resolve(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_WORDS).capsMode).isEqualTo(CapsMode.WORDS)
        assertThat(resolve(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_CHARACTERS).capsMode).isEqualTo(CapsMode.CHARACTERS)
    }

    @Test
    fun `urls and email get their keys, English, and no auto-correct`() {
        val url = resolve(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_URI)
        assertThat(url.kind).isEqualTo(FieldKind.URL)
        assertThat(url.allowAutoCorrect).isFalse()
        assertThat(url.allowSuggestions).isTrue()
        assertThat(url.languageOverride).isEqualTo(KeyboardLanguage.ENGLISH)

        val email = resolve(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        assertThat(email.kind).isEqualTo(FieldKind.EMAIL)
        assertThat(email.allowAutoCorrect).isFalse()
        assertThat(email.languageOverride).isEqualTo(KeyboardLanguage.ENGLISH)
    }

    @Test
    fun `force-ascii forces English, otherwise the app's language hint applies`() {
        assertThat(resolve(TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_FORCE_ASCII, hints = listOf("bn-BD")).languageOverride)
            .isEqualTo(KeyboardLanguage.ENGLISH)
        assertThat(resolve(TYPE_CLASS_TEXT, hints = listOf("bn-BD")).languageOverride).isEqualTo(KeyboardLanguage.BANGLA)
        assertThat(resolve(TYPE_CLASS_TEXT, hints = listOf("fr-FR", "en-GB")).languageOverride)
            .isEqualTo(KeyboardLanguage.ENGLISH)
        assertThat(resolve(TYPE_CLASS_TEXT, hints = listOf("fr-FR")).languageOverride).isNull()
        assertThat(resolve(TYPE_CLASS_TEXT).languageOverride).isNull()
    }

    @Test
    fun `numbers and phones are not words`() {
        for (type in listOf(TYPE_CLASS_NUMBER, TYPE_CLASS_PHONE)) {
            val p = resolve(type)
            assertThat(p.allowSuggestions).isFalse()
            assertThat(p.allowLearning).isTrue()
            assertThat(p.kind).isEqualTo(FieldKind.TEXT)
        }
    }

    @Test
    fun `custom enter label is kept unless enter is a plain newline`() {
        assertThat(resolve(TYPE_CLASS_TEXT, label = " Post ").enterLabel).isEqualTo("Post")
        assertThat(resolve(TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_ENTER_ACTION, label = "Post").enterLabel).isNull()
        assertThat(resolve(TYPE_CLASS_TEXT, label = "  ").enterLabel).isNull()
    }
}
