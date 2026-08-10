package com.bornomala.keyboard.ime.domain.model

import androidx.compose.runtime.Immutable

/**
 * Visual + behavioural style of a key, driving which color role from the theme it uses
 * and how much horizontal weight it claims in its row.
 */
@Immutable
enum class KeyStyle {
    /** Normal letter / digit key. */
    NORMAL,

    /** Functional keys: shift, backspace, symbols, enter, language. */
    FUNCTIONAL,

    /** The wide spacebar. */
    SPACEBAR,

    /** Accent/primary action (e.g. an active Enter that means "send"). */
    ACCENT,
}

/**
 * A glyph override for a key, used when the icon shown must differ from the one the renderer
 * would otherwise derive from [Key.action]. Kept as a tiny domain-safe enum (no Compose icon
 * types here) so the data layer can opt a specific key into a specific glyph; the presentation
 * layer maps each value to a concrete icon.
 *
 * Example: the numpad's right-column Space key shares [KeyAction.Space] with the main spacebar
 * but must render the dedicated space glyph, while the main spacebar keeps its plain label.
 */
@Immutable
enum class KeyIcon {
    /** The compact space glyph used on the numpad's space key. */
    SPACE,
}

/**
 * A single key in a layout, expressed purely as data. The layout tables build these once
 * at class-load time and reuse the same instances every keystroke, so rendering and input
 * handling allocate nothing per press.
 *
 * @param label primary glyph shown on the key (lowercase form for letters).
 * @param shiftedLabel glyph used when shift/caps is active; defaults to the uppercased
 *   [label] computed lazily by the renderer when null.
 * @param action what the key does when tapped.
 * @param hint small corner hint (e.g. the digit on a letter key, or first long-press char).
 * @param longPressChars characters offered in the long-press popup, in display order.
 * @param longPressKeyAction a discrete action fired when the key is held (no popup); used by
 *   keys with no character alternates, e.g. the language key holds to open the IME picker.
 * @param weight relative horizontal size within the row (1f = one standard key).
 * @param style visual role.
 * @param contentDescription accessibility label for TalkBack; when null the renderer
 *   derives a sensible default from the label/action.
 * @param repeatable whether holding the key repeats the action (backspace, space).
 * @param iconOverride forces a specific glyph regardless of [action]; checked before the
 *   action→icon fallback so two keys with the same action can render differently (e.g. the
 *   numpad space key vs. the main spacebar). Null (default) keeps the standard behaviour.
 * @param cursorControl turns the key into a hold-and-swipe cursor controller (the spacebar):
 *   a horizontal drag moves the text caret, a held-still press still repeats [action], and a
 *   quick tap fires [action] once. The renderer checks this before [repeatable], so a key with
 *   cursor control owns its own repeat handling and must leave [repeatable] false.
 */
@Immutable
data class Key(
    val label: String,
    val action: KeyAction,
    val shiftedLabel: String? = null,
    val hint: String? = null,
    val longPressChars: List<Char> = emptyList(),
    val longPressKeyAction: KeyAction? = null,
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.NORMAL,
    val contentDescription: String? = null,
    val repeatable: Boolean = false,
    val iconOverride: KeyIcon? = null,
    val cursorControl: Boolean = false,
) {
    companion object {
        /** Builds a standard letter key with an optional long-press accent set and digit hint. */
        fun letter(
            char: Char,
            hint: String? = null,
            longPress: String = "",
        ): Key = Key(
            label = char.toString(),
            action = KeyAction.Character(char),
            hint = hint,
            longPressChars = if (longPress.isEmpty()) emptyList() else longPress.toList(),
        )

        /** Builds a symbol / punctuation key. */
        fun symbol(
            char: Char,
            longPress: String = "",
        ): Key = Key(
            label = char.toString(),
            action = KeyAction.Character(char),
            longPressChars = if (longPress.isEmpty()) emptyList() else longPress.toList(),
        )
    }
}
