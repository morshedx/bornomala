package com.bornomala.keyboard.ime.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bornomala.keyboard.theme.BornomalaTheme
import com.bornomala.keyboard.theme.LucideIcons

/**
 * Stable section keys shared between the in-keyboard settings menu and the full settings app.
 * The IME passes one of these to the Settings activity (as an intent extra) so it can open
 * directly on the matching category. Kept as plain strings so `:keyboard` stays decoupled from
 * `:settings` — both sides reference the same constants by value, not by a shared type.
 */
object SettingsSections {
    const val APPEARANCE = "appearance"
    const val FEEDBACK = "feedback"
    const val TYPING = "typing"
    const val FEATURES = "features"
    const val BANGLA = "bangla"
    const val ABOUT = "about"
}

private data class MenuTile(
    val key: String,
    val icon: ImageVector,
    val label: String,
)

private val MENU_TILES = listOf(
    MenuTile(SettingsSections.APPEARANCE, LucideIcons.Palette, "Appearance"),
    MenuTile(SettingsSections.FEEDBACK, LucideIcons.Vibrate, "Feedback"),
    MenuTile(SettingsSections.TYPING, LucideIcons.Keyboard, "Typing"),
    MenuTile(SettingsSections.FEATURES, LucideIcons.Lightbulb, "Features"),
    MenuTile(SettingsSections.BANGLA, LucideIcons.Languages, "Bangla"),
    MenuTile(SettingsSections.ABOUT, LucideIcons.Info, "About"),
)

object SettingsMenuTestTags {
    const val PANEL = "settings_menu_panel"
    const val BACK = "settings_menu_back"
    fun tile(key: String) = "settings_menu_tile_$key"
}

/**
 * In-keyboard settings menu: a back arrow + title bar, then a 2-column grid of the app's
 * settings categories (Gboard tools-page style, not identical). Tapping a tile opens the full
 * settings app on that section via [onOpenSection]; the back arrow ([onBack]) returns to the
 * keyboard. Sized to fill the panel host so the IME window stays keyboard-height.
 */
@Composable
internal fun SettingsMenuPanel(
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BornomalaTheme.keyboardColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.keyboardBackground)
            .testTag(SettingsMenuTestTags.PANEL),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BornomalaTheme.dimens.panelTabStripHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack)
                    .testTag(SettingsMenuTestTags.BACK)
                    .clearAndSetSemantics { contentDescription = "Back to keyboard" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = LucideIcons.ArrowLeft,
                    contentDescription = null,
                    tint = colors.functionalKeyContent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = "Keyboard settings",
                color = colors.suggestionText,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 10.dp),
        ) {
            items(MENU_TILES, key = { it.key }) { tile ->
                MenuTileCell(tile = tile, onClick = { onOpenSection(tile.key) })
            }
        }
    }
}

@Composable
private fun MenuTileCell(tile: MenuTile, onClick: () -> Unit) {
    val colors = BornomalaTheme.keyboardColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.keyBackground)
            .clickable(onClick = onClick)
            .testTag(SettingsMenuTestTags.tile(tile.key))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.accentKeyBackground.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = colors.suggestionTextHighlighted,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = tile.label,
            color = colors.keyContent,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}
