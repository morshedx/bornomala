package com.bornomala.keyboard.ime.domain.model

/**
 * A full-area overlay shown in place of the key grid (above the action strip stays visible).
 * [NONE] means the normal keyboard is shown.
 */
enum class KeyboardPanel {
    /** Normal keyboard (key grid) is shown. */
    NONE,

    /** Clipboard history panel. */
    CLIPBOARD,

    /** Emoji picker panel. */
    EMOJI,
}
