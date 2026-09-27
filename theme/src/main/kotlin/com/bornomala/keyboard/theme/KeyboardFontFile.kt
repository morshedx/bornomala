package com.bornomala.keyboard.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.InputStream

/**
 * The keyboard's custom font ([KeyboardFont.CUSTOM]): one TTF/OTF file in the app's private
 * files, whether it came from Google Fonts or was imported from the phone. The keyboard only
 * ever reads this copy, so it needs no network and no Play services while typing, and deleting
 * the original file does not affect it. All functions do disk I/O: call them off the main thread.
 */
object KeyboardFontFile {
    private const val FILE_NAME = "keyboard_font"

    /** Refuse anything bigger: real fonts for Latin key labels are far smaller. */
    private const val MAX_BYTES = 20L * 1024 * 1024

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Imports the font at [uri] (a picked file). Returns false if it isn't a usable font. */
    fun import(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { save(context, it) } ?: false
    }.getOrDefault(false)

    /**
     * Saves the font read from [input] as the keyboard font. The bytes go to a temporary file
     * first and are checked by building a [Typeface] from them; only a valid font replaces the
     * current one. Returns false for anything that isn't a font (or is too large).
     */
    fun save(context: Context, input: InputStream): Boolean = runCatching {
        val target = file(context)
        val tmp = File(target.parentFile, "$FILE_NAME.tmp")
        tmp.outputStream().use { out ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) return@runCatching false.also { tmp.delete() }
                out.write(buffer, 0, n)
            }
        }
        if (!isFont(tmp)) {
            tmp.delete()
            return@runCatching false
        }
        tmp.renameTo(target)
    }.getOrDefault(false)

    /** The saved font as a [FontFamily], or null when there is none (or it can't be read). */
    fun load(context: Context): FontFamily? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching { FontFamily(Typeface.createFromFile(f)) }.getOrNull()
    }

    /**
     * [Typeface.createFromFile] quietly falls back to the default typeface for data it can't
     * parse, so a file only counts as a font when it yields a typeface that isn't the default.
     */
    private fun isFont(f: File): Boolean {
        val typeface = runCatching { Typeface.createFromFile(f) }.getOrNull() ?: return false
        return typeface != Typeface.DEFAULT
    }
}
