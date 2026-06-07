package com.bornomala.keyboard.ime.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hand-built Lucide icons (https://lucide.dev, ISC license) as Compose [ImageVector]s, so we
 * get the exact Lucide look without bundling the whole icon font. Stroke-based, 24x24, round
 * caps/joins — matching Lucide's defaults. Tint is applied by the `Icon` composable.
 */
internal object LucideIcons {

    /** `clipboard-list`. */
    val ClipboardList: ImageVector by lazy {
        lucide("ClipboardList") {
            // Clip (top tab).
            path("M9 2 h6 a1 1 0 0 1 1 1 v2 a1 1 0 0 1 -1 1 h-6 a1 1 0 0 1 -1 -1 v-2 a1 1 0 0 1 1 -1 z")
            // Board body.
            path("M16 4 h2 a2 2 0 0 1 2 2 v14 a2 2 0 0 1 -2 2 H6 a2 2 0 0 1 -2 -2 V6 a2 2 0 0 1 2 -2 h2")
            // List lines + bullets.
            path("M12 11 h4")
            path("M12 16 h4")
            path("M8 11 h0.01")
            path("M8 16 h0.01")
        }
    }

    private class Builder(private val b: ImageVector.Builder) {
        fun path(d: String) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }

    private inline fun lucide(name: String, block: Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).also { Builder(it).block() }.build()
}
