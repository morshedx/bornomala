package com.bornomala.keyboard.settings.data.fonts

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import com.bornomala.keyboard.settings.R
import com.bornomala.keyboard.theme.KeyboardFontFile

/** One family from the bundled Google Fonts catalog. */
@Immutable
data class GoogleFont(val family: String, val category: GoogleFontCategory)

/** Google Fonts' categories, used as filter chips in the picker. */
enum class GoogleFontCategory(val key: String) {
    SANS("sans"),
    SERIF("serif"),
    DISPLAY("display"),
    HANDWRITING("handwriting"),
    MONO("mono"),
    ;

    companion object {
        fun fromKey(key: String): GoogleFontCategory = entries.firstOrNull { it.key == key } ?: SANS
    }
}

/**
 * Google Fonts through Google Play services' font provider: Play services downloads each font
 * once and caches it on the phone, shared with other apps. The app needs no API key and makes
 * no network requests of its own for this.
 *
 * The family list is bundled (`assets/google_fonts.tsv`, built by `scripts/gen_google_fonts.py`)
 * because the provider can fetch a family by name but cannot list them.
 *
 * Every function blocks on disk or the provider: call them off the main thread.
 */
object GoogleFonts {
    private const val PROVIDER_AUTHORITY = "com.google.android.gms.fonts"
    private const val PROVIDER_PACKAGE = "com.google.android.gms"
    private const val CATALOG_ASSET = "google_fonts.tsv"

    /** Whether this phone has Play services' font provider (absent on phones without Google). */
    fun isAvailable(context: Context): Boolean =
        runCatching { context.packageManager.resolveContentProvider(PROVIDER_AUTHORITY, 0) != null }
            .getOrDefault(false)

    /** The bundled catalog, most popular first. */
    fun catalog(context: Context): List<GoogleFont> =
        context.assets.open(CATALOG_ASSET).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else GoogleFont(line.substring(0, tab), GoogleFontCategory.fromKey(line.substring(tab + 1)))
            }.toList()
        }

    /** The family as a [Typeface] for previews, or null if it can't be fetched right now. */
    fun typeface(context: Context, family: String): Typeface? = runCatching {
        val fonts = fetch(context, family) ?: return null
        FontsContractCompat.buildTypeface(context, null, fonts)
    }.getOrNull()

    /**
     * Downloads [family] (through Play services) and saves it as the keyboard font file.
     * Returns false when it can't be fetched (offline, or the family isn't served).
     */
    fun saveAsKeyboardFont(context: Context, family: String): Boolean = runCatching {
        val font = fetch(context, family)?.firstOrNull() ?: return false
        context.contentResolver.openInputStream(font.uri)?.use { KeyboardFontFile.save(context, it) } ?: false
    }.getOrDefault(false)

    private fun fetch(context: Context, family: String): Array<FontsContractCompat.FontInfo>? {
        val request = FontRequest(
            PROVIDER_AUTHORITY,
            PROVIDER_PACKAGE,
            "name=$family",
            R.array.com_google_android_gms_fonts_certs,
        )
        val result = FontsContractCompat.fetchFonts(context, null, request)
        if (result.statusCode != FontsContractCompat.FontFamilyResult.STATUS_OK) return null
        return result.fonts.filter { it.resultCode == FontsContractCompat.Columns.RESULT_CODE_OK }
            .takeIf { it.isNotEmpty() }
            ?.toTypedArray()
    }
}
