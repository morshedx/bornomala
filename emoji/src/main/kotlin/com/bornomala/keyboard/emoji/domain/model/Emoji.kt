package com.bornomala.keyboard.emoji.domain.model

import androidx.compose.runtime.Immutable

/**
 * A single emoji entry from the bundled catalog.
 *
 * Immutable and allocation-free to construct on the hot path: the [keywords] list is
 * shared from the prebuilt catalog and never copied per-render. [glyph] is the actual
 * Unicode string committed to the [android.view.inputmethod.InputConnection].
 *
 * @param glyph the Unicode emoji sequence (may be multi-codepoint, e.g. with skin tone
 *   modifiers or ZWJ sequences).
 * @param name human-readable canonical name, used as the primary search field and the
 *   accessibility content description.
 * @param keywords additional search terms (synonyms) for the search index.
 * @param category the static catalog category this emoji belongs to.
 */
@Immutable
data class Emoji(
    val glyph: String,
    val name: String,
    val keywords: List<String>,
    val category: EmojiCategory,
)
