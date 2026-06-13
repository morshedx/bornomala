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
 * @param scrollableLeftStrip optional vertically-scrollable column of keys pinned to the left
 *   of the grid (the numpad's symbol strip). When non-null the renderer places this column on
 *   the left — one key wide, only a few keys tall, the rest reachable by scrolling — alongside
 *   the grid [rows] (whose last row is treated as a full-width bottom row beneath the strip).
 *   Null (default) means the layout renders as plain weighted rows, unchanged for every other
 *   layout.
 */
@Immutable
data class KeyboardLayout(
    val id: String,
    val rows: List<KeyRow>,
    val scrollableLeftStrip: List<Key>? = null,
)

/**
 * One horizontal row of keys.
 *
 * @param keys the keys, distributed left-to-right by [Key.weight].
 * @param edgeWeight blank gutter on each side of the row, expressed in key-widths (same unit as
 *   [Key.weight]). The home row (a–l) uses 0.5 so it sits half a key in from both edges —
 *   the standard Gboard/Samsung indent that keeps the 9 home keys aligned under the 10 top-row
 *   keys instead of stretching edge-to-edge. 0 (default) means flush, used by every other row.
 */
@Immutable
data class KeyRow(
    val keys: List<Key>,
    val edgeWeight: Float = 0f,
)
