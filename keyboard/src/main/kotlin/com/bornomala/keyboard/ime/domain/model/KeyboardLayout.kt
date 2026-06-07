package com.bornomala.keyboard.ime.domain.model

import androidx.compose.runtime.Immutable

/**
 * A keyboard layout is an ordered list of rows, each an ordered list of [Key]s. Rows are
 * laid out top-to-bottom; within a row keys are distributed by their [Key.weight].
 *
 * Layouts are immutable and built once (see `data.layout.*`), then selected per
 * (language, page, numberRow) combination — never rebuilt per keystroke.
 *
 * @param id stable identifier, useful for tests and debugging.
 * @param rows the key grid.
 */
@Immutable
data class KeyboardLayout(
    val id: String,
    val rows: List<KeyRow>,
)

/**
 * One horizontal row of keys.
 */
@Immutable
data class KeyRow(
    val keys: List<Key>,
)
