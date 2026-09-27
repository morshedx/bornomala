package com.bornomala.keyboard.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The photo behind the keys for [KeyboardTheme.IMAGE], and how much to darken it so the white
 * key labels stay readable.
 *
 * @param dim 0..1 alpha of a black layer drawn over the photo.
 */
@Immutable
class KeyboardBackground(val image: ImageBitmap, val dim: Float)

/** The active photo background, or null for the plain theme colours. */
val LocalKeyboardBackground = staticCompositionLocalOf<KeyboardBackground?> { null }

/**
 * Paints the keyboard tray: [color], then (when [background] is set) the photo scaled to cover
 * the area and centred, then the dim layer. Drawn once per size change, not per key press.
 */
fun Modifier.keyboardTray(color: Color, background: KeyboardBackground?): Modifier =
    if (background == null) {
        background(color)
    } else {
        drawBehind {
            drawRect(color)
            val image = background.image
            val scale = max(size.width / image.width, size.height / image.height)
            val srcWidth = (size.width / scale).roundToInt().coerceIn(1, image.width)
            val srcHeight = (size.height / scale).roundToInt().coerceIn(1, image.height)
            drawImage(
                image = image,
                srcOffset = IntOffset((image.width - srcWidth) / 2, (image.height - srcHeight) / 2),
                srcSize = IntSize(srcWidth, srcHeight),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
            if (background.dim > 0f) drawRect(Color.Black.copy(alpha = background.dim.coerceIn(0f, 1f)))
        }
    }

/**
 * The keyboard photo, stored privately in the app's files (never shared or uploaded). Picking a
 * photo copies it here, upright and scaled down to at most [MAX_EDGE] px, so the keyboard only
 * ever decodes a small file. All functions do disk I/O: call them off the main thread.
 */
object KeyboardBackgroundImage {
    private const val FILE_NAME = "keyboard_background.jpg"
    private const val MAX_EDGE = 1440
    private const val JPEG_QUALITY = 90

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Copies the image at [uri] in as the keyboard photo. Returns false if it can't be read. */
    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val bitmap = decodeScaled(context, uri) ?: return false
        val target = file(context)
        val tmp = File(target.parentFile, "$FILE_NAME.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        tmp.renameTo(target)
    }.getOrDefault(false)

    /** The stored photo, or null when there is none (or it can't be decoded). */
    fun load(context: Context): ImageBitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the EXIF rotation, so camera photos come out upright.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = MAX_EDGE.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
}
