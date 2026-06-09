package com.bornomala.keyboard.ime.presentation

import com.bornomala.keyboard.theme.LucideIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bornomala.keyboard.ime.domain.model.Suggestion
import com.bornomala.keyboard.theme.BornomalaTheme

/**
 * The single top strip above the keys. It shows EITHER the suggestion candidates (while
 * typing) OR the quick-action tools, never both — toggled by a fixed button on the left:
 *
 *  - When suggestions arrive, the strip auto-switches to suggestions (tools collapse).
 *  - The left button flips between the two views on demand.
 *
 * Keeping one strip (instead of a toolbar + a suggestion bar) avoids the empty band and
 * matches the Gboard layout. The left toggle is always present so the tools are reachable.
 */
@Composable
internal fun ActionStrip(
    suggestions: List<Suggestion>,
    emojiActive: Boolean,
    clipboardActive: Boolean,
    numpadActive: Boolean,
    settingsActive: Boolean,
    callbacks: KeyboardCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = BornomalaTheme.keyboardColors
    val hasSuggestions = suggestions.isNotEmpty()
    var toolsExpanded by remember { mutableStateOf(false) }

    // New suggestions pull focus back to the suggestion view automatically.
    LaunchedEffect(hasSuggestions) {
        if (hasSuggestions) toolsExpanded = false
    }

    val showTools = !hasSuggestions || toolsExpanded

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.suggestionBarBackground)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StripIconButton(
            icon = if (showTools) LucideIcons.ChevronRight else LucideIcons.ChevronLeft,
            description = if (showTools) "Show suggestions" else "Show tools",
            onClick = { toolsExpanded = !showTools },
        )

        if (showTools) {
            ToolsRow(
                emojiActive = emojiActive,
                numpadActive = numpadActive,
                clipboardActive = clipboardActive,
                settingsActive = settingsActive,
                callbacks = callbacks,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        } else {
            SuggestionBar(
                suggestions = suggestions,
                onSuggestion = callbacks.onSuggestion,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ToolsRow(
    emojiActive: Boolean,
    numpadActive: Boolean,
    clipboardActive: Boolean,
    settingsActive: Boolean,
    callbacks: KeyboardCallbacks,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StripIconButton(LucideIcons.Smile, "Emoji", callbacks.onToggleEmoji, active = emojiActive)
        NumbersButton(active = numpadActive, onClick = callbacks.onToggleNumbers)
        StripIconButton(LucideIcons.ClipboardList, "Clipboard", callbacks.onToggleClipboard, active = clipboardActive)
        StripIconButton(LucideIcons.Settings, "Keyboard settings", callbacks.onToggleSettingsMenu, active = settingsActive)
        StripIconButton(LucideIcons.ChevronDown, "Hide keyboard", callbacks.onHideKeyboard)
    }
}

/**
 * The number-pad toggle button, drawn as a real 2x2 grid (1 2 / 3 4) with each digit
 * centered in its own equal cell, so the spacing between digits is even both ways.
 */
@Composable
private fun NumbersButton(active: Boolean, onClick: () -> Unit) {
    val colors = BornomalaTheme.keyboardColors
    val tint = if (active) colors.suggestionTextHighlighted else colors.functionalKeyContent

    @Composable
    fun cell(digit: String) {
        Box(modifier = Modifier.size(width = 11.dp, height = 10.dp), contentAlignment = Alignment.Center) {
            Text(digit, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Medium, lineHeight = 9.sp)
        }
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Number pad" },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { cell("1"); cell("2") }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { cell("3"); cell("4") }
        }
    }
}

@Composable
private fun StripIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val colors = BornomalaTheme.keyboardColors
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) colors.suggestionTextHighlighted else colors.functionalKeyContent,
            modifier = Modifier.size(22.dp),
        )
    }
}
