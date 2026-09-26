package com.bornomala.keyboard.ime.domain.model

/**
 * What the Enter key does in the focused field, resolved from the editor's `imeOptions`.
 * Drives both the key's glyph/accent (Gboard-style ✓ / search / send / → ) and whether Enter
 * performs the field's editor action or inserts a newline.
 *
 * @param isAccent whether Enter is styled as the accent (primary) action for this field.
 * @param label spoken content description for accessibility.
 */
enum class EnterAction(val isAccent: Boolean, val label: String) {
    /** Plain Enter: multi-line fields, or fields that declare no action. */
    NEWLINE(false, "Enter"),
    DONE(true, "Done"),
    GO(true, "Go"),
    SEARCH(true, "Search"),
    SEND(true, "Send"),
    NEXT(true, "Next"),
    PREVIOUS(true, "Previous"),
}
