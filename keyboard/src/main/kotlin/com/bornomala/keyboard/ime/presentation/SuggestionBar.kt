package com.bornomala.keyboard.ime.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
    // Background fills the bar; the chips live in a horizontally scrollable row so showing up
    // to MAX_VISIBLE candidates never truncates a long word — overflow scrolls instead.
    Box(modifier = modifier.fillMaxWidth().background(colors.suggestionBarBackground)) {
        if (suggestions.isEmpty()) return@Box
        val shown = if (suggestions.size > MAX_VISIBLE) suggestions.subList(0, MAX_VISIBLE) else suggestions
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shown.forEachIndexed { index, suggestion ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                            .background(colors.suggestionDivider),
                    )
                }
                val highlighted = suggestion.isAutoCorrect
                Text(
                    text = suggestion.text,
                    color = if (highlighted) colors.suggestionTextHighlighted else colors.suggestionText,
                    fontSize = 15.sp,
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(min = 72.dp)
                        .clickable { onSuggestion(suggestion.text) }
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}

private const val MAX_VISIBLE = 6
