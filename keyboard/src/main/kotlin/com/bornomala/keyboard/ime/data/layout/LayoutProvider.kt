package com.bornomala.keyboard.ime.data.layout

import com.bornomala.keyboard.ime.domain.model.KeyRow
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.KeyboardLayout
import com.bornomala.keyboard.ime.domain.model.KeyboardPage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the immutable [KeyboardLayout] for a given (language, page, number-row)
 * combination. Every distinct combination is pre-built once at construction and stored in
 * a small fixed map, so [layoutFor] is a pure lookup with zero allocation on the
 * per-keystroke / per-recomposition path.
 *
 * Number-row variants are produced by prepending the shared number row to the base layout;
 * symbol pages already include digits, so they are never given an extra number row.
 */
@Singleton
class LayoutProvider @Inject constructor() {

    private data class LayoutKey(
        val language: KeyboardLanguage,
        val page: KeyboardPage,
        val numberRow: Boolean,
        val email: Boolean,
    )

    private val cache: Map<LayoutKey, KeyboardLayout> = buildCache()

    /**
     * @param language active language (only matters for the ALPHA page).
     * @param page active page.
     * @param showNumberRow whether to prepend the dedicated number row (alpha page only).
     * @param emailField whether the editor is an email field (comma becomes "@", alpha only).
     */
    fun layoutFor(
        language: KeyboardLanguage,
        page: KeyboardPage,
        showNumberRow: Boolean,
        emailField: Boolean = false,
    ): KeyboardLayout {
        // Symbol/numpad pages have their own bottom keys; the number-row and email tweaks only
        // apply to the alpha page, so collapse them there to hit shared cached instances.
        val isAlpha = page == KeyboardPage.ALPHA
        return cache.getValue(
            LayoutKey(language, page, showNumberRow && isAlpha, emailField && isAlpha),
        )
    }

    private fun buildCache(): Map<LayoutKey, KeyboardLayout> {
        val result = HashMap<LayoutKey, KeyboardLayout>()
        for (language in KeyboardLanguage.entries) {
            for (page in KeyboardPage.entries) {
                for (email in listOf(false, true)) {
                    val base = baseLayout(language, page, email)
                    result[LayoutKey(language, page, numberRow = false, email = email)] = base
                    val withNumbers = if (page == KeyboardPage.ALPHA) withNumberRow(base, language) else base
                    result[LayoutKey(language, page, numberRow = true, email = email)] = withNumbers
                }
            }
        }
        return result
    }

    private fun baseLayout(
        language: KeyboardLanguage,
        page: KeyboardPage,
        email: Boolean,
    ): KeyboardLayout =
        when (page) {
            KeyboardPage.ALPHA -> {
                val layout = when (language) {
                    KeyboardLanguage.ENGLISH -> EnglishLayout.QWERTY
                    KeyboardLanguage.BANGLA -> BanglaLayout.AVRO_PHONETIC
                }
                if (email) emailVariant(layout, language) else layout
            }
            KeyboardPage.SYMBOLS -> SymbolsLayout.PAGE_ONE
            KeyboardPage.SYMBOLS_EXTRA -> SymbolsLayout.PAGE_TWO
            KeyboardPage.NUMPAD -> NumpadLayout.PAD
        }

    /** Replaces the alpha layout's bottom row with the email variant (comma -> "@"). */
    private fun emailVariant(layout: KeyboardLayout, language: KeyboardLanguage): KeyboardLayout {
        val rows = layout.rows.toMutableList()
        rows[rows.size - 1] = SharedKeys.bottomRow(language.displayName, emailField = true)
        return layout.copy(id = layout.id + "_email", rows = rows)
    }

    private fun withNumberRow(layout: KeyboardLayout, language: KeyboardLanguage): KeyboardLayout {
        val numberRow = if (language == KeyboardLanguage.BANGLA) {
            SharedKeys.BANGLA_NUMBER_ROW
        } else {
            SharedKeys.NUMBER_ROW
        }
        val rows = ArrayList<KeyRow>(layout.rows.size + 1)
        rows.add(numberRow)
        rows.addAll(layout.rows)
        return layout.copy(id = layout.id + "_numrow", rows = rows)
    }
}
