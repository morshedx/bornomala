package com.bornomala.keyboard.suggestions.data.dictionary

import android.content.Context
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads bundled frequency dictionaries from the module's `assets/` directory.
 *
 * The asset files are plain UTF-8 `word<TAB>frequency` text, one entry per line. They
 * are read lazily on first use (never at IME `onCreate`) on an I/O dispatcher by the
 * caller. The returned sequence buffers the reader and closes it when fully consumed.
 */
@Singleton
class AssetDictionarySource @Inject constructor(
    @ApplicationContext private val context: Context,
) : DictionarySource {

    override fun linesFor(language: SuggestionLanguage): Sequence<String> {
        val assetName = when (language) {
            SuggestionLanguage.ENGLISH -> ASSET_ENGLISH
            SuggestionLanguage.BANGLA -> ASSET_BANGLA
        }
        return sequence {
            context.applicationContext.assets.open("$ASSET_DIR/$assetName").use { stream ->
                val reader: BufferedReader = stream.bufferedReader(Charsets.UTF_8)
                reader.use { r ->
                    var line = r.readLine()
                    while (line != null) {
                        yield(line)
                        line = r.readLine()
                    }
                }
            }
        }
    }

    private companion object {
        const val ASSET_DIR = "dictionaries"
        const val ASSET_ENGLISH = "en_frequency.txt"
        const val ASSET_BANGLA = "bn_frequency.txt"
    }
}
