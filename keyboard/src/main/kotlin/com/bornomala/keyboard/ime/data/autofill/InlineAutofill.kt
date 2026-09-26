package com.bornomala.keyboard.ime.data.autofill

import android.content.Context
import android.os.Build
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Inline autofill (Android 11+): lets the user's password manager / autofill service offer
 * logins, addresses and one-time codes as chips in the keyboard's top strip, as Gboard does.
 *
 * The keyboard only hosts the chips: they are rendered by the autofill service inside system
 * views, and tapping one fills the field through the platform. The keyboard never sees the
 * credential. Responses are tagged with a per-field generation so a slow response for a field
 * the user has already left is dropped. Only [createRequest] and [onResponse] need Android 11;
 * the host constructs this only there, but [views] and [clear] are safe on any version.
 */
class InlineAutofill(private val context: Context) {

    private val _views = MutableStateFlow<List<View>>(emptyList())

    /** Inflated chips for the focused field, in the service's order; empty when none. */
    val views: StateFlow<List<View>> = _views.asStateFlow()

    private var generation = 0

    /** What the keyboard can show: up to [MAX_SUGGESTIONS] single-line chips. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createRequest(): InlineSuggestionsRequest {
        val density = context.resources.displayMetrics.density
        val height = (CHIP_HEIGHT_DP * density).toInt()
        val style = UiVersions.newStylesBuilder()
            .addStyle(InlineSuggestionUi.newStyleBuilder().build())
            .build()
        val spec = InlinePresentationSpec.Builder(
            Size((CHIP_MIN_WIDTH_DP * density).toInt(), height),
            Size((CHIP_MAX_WIDTH_DP * density).toInt(), height),
        ).setStyle(style).build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(MAX_SUGGESTIONS)
            .build()
    }

    /** Inflates the chips of [response] and publishes them once all are ready. Always handled. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun onResponse(response: InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
        val current = ++generation
        if (suggestions.isEmpty()) {
            _views.value = emptyList()
            return true
        }
        val inflated = arrayOfNulls<View>(suggestions.size)
        var pending = suggestions.size
        val size = Size(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        suggestions.forEachIndexed { index, suggestion ->
            suggestion.inflate(context, size, context.mainExecutor) { view ->
                inflated[index] = view
                pending--
                if (pending == 0 && current == generation) _views.value = inflated.filterNotNull()
            }
        }
        return true
    }

    /** Drops the current chips (field changed or finished). */
    fun clear() {
        generation++
        if (_views.value.isNotEmpty()) _views.value = emptyList()
    }

    private companion object {
        const val MAX_SUGGESTIONS = 6
        const val CHIP_HEIGHT_DP = 36
        const val CHIP_MIN_WIDTH_DP = 64
        const val CHIP_MAX_WIDTH_DP = 320
    }
}
