package com.bornomala.keyboard.ime.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.bornomala.keyboard.ime.domain.model.Suggestion
import com.bornomala.keyboard.theme.BornomalaTheme

/**
 * The strip above the keys showing word candidates. Tapping a chip commits that word.
 *
 * Performance: this composable only recomposes when the suggestion list instance changes
 * (the state holder emits a new immutable list), not on every keystroke. Each chip is a
 * lightweight clickable [Text]; no ripple/animation is used so taps stay within budget.
 *
 * Accessibility: chips are real clickable nodes with their text as the label, so TalkBack
 * announces and activates each candidate.
 */
@Composable
internal fun SuggestionBar(
    suggestions: List<Suggestion>,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BornomalaTheme.keyboardColors
    // Gboard-style strip: equal-width slots spanning the full bar, with the top (primary)
    // candidate placed in the CENTER slot and emphasized; the remaining candidates fan out
    // symmetrically around it. An odd visible count keeps the primary exactly centered.
    Box(modifier = modifier.fillMaxWidth().background(colors.suggestionBarBackground)) {
        if (suggestions.isEmpty()) return@Box
        val shown = if (suggestions.size > MAX_VISIBLE) suggestions.subList(0, MAX_VISIBLE) else suggestions
        val primary = shown.first()
        val ordered = centerPrimary(shown)
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ordered.forEachIndexed { index, suggestion ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                            .background(colors.suggestionDivider),
                    )
                }
                val isPrimary = suggestion === primary
                // The center/primary chip is always bold; it takes the highlight colour only
                // when it is an actual auto-correct (so a plain top completion isn't mis-coloured).
                val highlighted = isPrimary && suggestion.isAutoCorrect
                Text(
                    text = suggestion.text,
                    color = if (highlighted) colors.suggestionTextHighlighted else colors.suggestionText,
                    fontSize = 15.sp,
                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSuggestion(suggestion.text) }
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/**
 * Reorders [shown] so the primary (index 0) sits in the middle slot and the rest fan out
 * symmetrically: 2nd to its right, 3rd to its left, 4th right, 5th left, … For an odd size the
 * primary lands exactly at the centre; e.g. [a,b,c,d,e] -> [e,c,a,b,d].
 */
private fun centerPrimary(shown: List<Suggestion>): List<Suggestion> {
    val n = shown.size
    if (n <= 1) return shown
    val result = arrayOfNulls<Suggestion>(n)
    val mid = (n - 1) / 2 // for an even count this leans one slot left of true centre
    result[mid] = shown[0]
    var left = mid - 1
    var right = mid + 1
    var i = 1
    // Fan the rest out right-then-left, but always within bounds so even sizes never overflow.
    while (i < n) {
        if (right < n) {
            result[right++] = shown[i]; i++
        }
        if (i < n && left >= 0) {
            result[left--] = shown[i]; i++
        }
    }
    @Suppress("UNCHECKED_CAST")
    return result.toList() as List<Suggestion>
}

// Odd cap so the primary slot is exactly centered.
private const val MAX_VISIBLE = 5
