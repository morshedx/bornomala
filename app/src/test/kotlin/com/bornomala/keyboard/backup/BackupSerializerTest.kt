package com.bornomala.keyboard.backup

import com.bornomala.keyboard.clipboard.domain.model.ClipboardItem
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.suggestions.data.local.LearnedNgramEntity
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryEntity
import com.bornomala.keyboard.theme.KeyboardFont
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** org.json is only real under Robolectric on the JVM, so this round-trips through it. */
@RunWith(RobolectricTestRunner::class)
class BackupSerializerTest {

    private val serializer = BackupSerializer()

    @Test
    fun `encode then decode preserves settings, dictionary and clipboard`() {
        val data = BackupData(
            schemaVersion = BackupData.SCHEMA_VERSION,
            appVersion = "0.7.1",
            createdAt = 1_700_000_000_000L,
            device = "Pixel Test",
            settings = Settings.DEFAULTS.copy(
                autoCorrectEnabled = false,
                blockOffensiveWords = false,
                keyboardHeightScale = 1.2f,
                keyboardFont = KeyboardFont.entries.last(),
            ),
            words = listOf(UserDictionaryEntity("hello", "en", 5, 99L, "say")),
            ngrams = listOf(LearnedNgramEntity("the", "cat", "en", 3, 88L)),
            clips = listOf(ClipboardItem(1L, "clip text", pinned = true, createdAt = 77L)),
        )

        val out = serializer.decode(serializer.encode(data))

        assertThat(out.settings.autoCorrectEnabled).isFalse()
        assertThat(out.settings.blockOffensiveWords).isFalse()
        assertThat(out.settings.keyboardHeightScale).isWithin(0.001f).of(1.2f)
        assertThat(out.settings.keyboardFont).isEqualTo(KeyboardFont.entries.last())
        assertThat(out.words).hasSize(1)
        assertThat(out.words.first().word).isEqualTo("hello")
        assertThat(out.words.first().frequency).isEqualTo(5)
        assertThat(out.ngrams.first().context).isEqualTo("the")
        assertThat(out.ngrams.first().word).isEqualTo("cat")
        assertThat(out.clips.first().text).isEqualTo("clip text")
        assertThat(out.clips.first().pinned).isTrue()
        assertThat(out.appVersion).isEqualTo("0.7.1")
    }
}
