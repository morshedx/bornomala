package com.bornomala.keyboard.theme

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.util.WeakHashMap

/**
 * How far to move a label, in ems, so its visible letters are centred on the key for any font.
 *
 * Centring the text's line box is not enough: fonts put their letters at very different heights
 * inside it (script and display fonts often have a tall ascent or deep descent), which pushes
 * the letters up or down. As Android's own keyboard does, the label is instead placed by the
 * font's measured letter height: capitals by the height of "H", small letters by the height of
 * "x" (so every lowercase key in a row shares one baseline).
 */
class LabelMetrics internal constructor(private val capShift: Float, private val xShift: Float) {
    /** The shift in ems (positive = down) for [label]. */
    fun shiftFor(label: String): Float = when {
        label.isEmpty() -> 0f
        // Scripts other than Latin (the Bangla digits and space-bar label) may be drawn from a
        // fallback font whose shape these metrics don't describe: leave them centred as laid out.
        label.any { it.code > LATIN_EXTENDED_END } -> 0f
        label.length == 1 && label[0].isLowerCase() -> xShift
        else -> capShift
    }
}

private const val LATIN_EXTENDED_END = 0x024F

/** Measured once per typeface (labels are drawn on the main thread only). */
private val LabelMetricsCache = WeakHashMap<Typeface, LabelMetrics>()

private val NoShift = LabelMetrics(0f, 0f)

object GlyphCentering {
    /** Centring metrics for [typeface]; no shift when it is null. */
    fun metricsFor(typeface: Typeface?): LabelMetrics = labelMetrics(typeface)
}

private fun labelMetrics(typeface: Typeface?): LabelMetrics {
    if (typeface == null) return NoShift
    return LabelMetricsCache.getOrPut(typeface) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = METRICS_TEXT_SIZE
        }
        val fm = paint.fontMetrics
        val bounds = Rect()
        paint.getTextBounds("H", 0, 1, bounds)
        val capHeight = -bounds.top.toFloat()
        paint.getTextBounds("x", 0, 1, bounds)
        val xHeight = -bounds.top.toFloat()
        // Centring the line box puts the baseline (-ascent - descent) / 2 below the key's middle;
        // centring a letter of height h needs it h / 2 below. The difference, in ems:
        val box = fm.ascent + fm.descent
        LabelMetrics(
            capShift = (capHeight + box) / (2 * METRICS_TEXT_SIZE),
            xShift = (xHeight + box) / (2 * METRICS_TEXT_SIZE),
        )
    }
}

private const val METRICS_TEXT_SIZE = 100f
