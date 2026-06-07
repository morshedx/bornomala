package com.bornomala.keyboard.ime.domain.model

/**
 * The active "page" of the keyboard. Alphabetic pages are language-specific (handled via
 * [KeyboardLanguage]); the symbol pages are shared across languages. SYMBOLS and
 * SYMBOLS_EXTRA form a two-level symbol set reachable via the "=\<" toggle, matching the
 * Samsung keyboard's behaviour.
 */
enum class KeyboardPage {
    /** The primary letter layout for the active language. */
    ALPHA,

    /** First symbols/numbers page (digits row + common punctuation). */
    SYMBOLS,

    /** Second symbols page (less common math / currency / brackets). */
    SYMBOLS_EXTRA,

    /** Calculator-style numeric pad (3x3 digits + math/edit keys), opened from the toolbar. */
    NUMPAD,
}
