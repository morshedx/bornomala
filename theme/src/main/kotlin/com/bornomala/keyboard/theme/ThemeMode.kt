package com.bornomala.keyboard.theme

/**
 * User-selectable theme preference. Persisted by the `:settings` module (DataStore)
 * and resolved to a concrete light/dark scheme by [BornomalaTheme].
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}
