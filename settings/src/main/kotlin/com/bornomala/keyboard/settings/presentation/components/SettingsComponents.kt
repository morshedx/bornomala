package com.bornomala.keyboard.settings.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bornomala.keyboard.settings.R
import com.bornomala.keyboard.theme.LucideIcons

/**
 * Building blocks for the settings screens, in the flat Android/Gboard settings style: a large
 * title that collapses into the top bar on scroll, full-width rows with no card backgrounds,
 * and small accent-coloured section headers.
 *
 * Interactive rows use the proper semantics modifier ([toggleable] / [selectable] / clickable)
 * so TalkBack announces role and state, and each meets the 48dp minimum touch target. Text uses
 * theme typography so it scales with the system font size.
 */

/** Horizontal inset shared by every row and header, so text lines up down the screen. */
private val RowInset = 24.dp

/** Row titles: large but regular weight, as in the Android/Gboard settings (the theme's is bold). */
@Composable
private fun RowTitleStyle(): TextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal)

/**
 * A settings screen: large collapsing title, optional back arrow, and a scrolling column.
 * [pinned] sits between the title and the column and does not scroll (e.g. a live preview);
 * [bottomOverlay] is drawn over the scroll content (e.g. a floating button). With [scrollable]
 * false the content supplies its own scrolling (e.g. a lazy list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    pinned: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    bottomOverlay: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = snackbarHost,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(LucideIcons.ArrowLeft, contentDescription = stringResource(R.string.settings_back))
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            pinned()
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = if (scrollable) {
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    } else {
                        Modifier.fillMaxSize()
                    },
                    content = content,
                )
                bottomOverlay()
            }
        }
    }
}

/** Small accent-coloured group label ("Keys", "Key press"), as in Gboard's settings. */
@Composable
internal fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = RowInset, end = RowInset, top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** A row that opens another screen: leading icon, title, optional one-line summary. */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = RowInset, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(24.dp))
        TitleAndDescription(title = title, description = summary.orEmpty(), enabled = true, modifier = Modifier.weight(1f))
    }
}

/** A plain tappable row (no icon), e.g. a destructive action; [titleColor] tints the title. */
@Composable
fun SettingsActionRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = RowInset, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = RowTitleStyle(),
            color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor,
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SwitchSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val stateText = stringResource(if (checked) R.string.settings_state_on else R.string.settings_state_off)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics { stateDescription = stateText }
            .padding(horizontal = RowInset, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TitleAndDescription(
            title = title,
            description = description,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(20.dp))
        // Decorative for a11y: the whole row is the toggle target.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
internal fun SliderSettingRow(
    title: String,
    description: String,
    valueLabel: String,
    sliderContentDescription: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowInset, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TitleAndDescription(
                title = title,
                description = description,
                enabled = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = sliderContentDescription },
        )
    }
}

/**
 * A single-choice group rendered as radio rows. Uses [selectableGroup] so TalkBack announces
 * "n of m" position within the group.
 */
@Composable
internal fun <T> RadioSettingGroup(
    title: String,
    description: String,
    options: List<RadioOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TitleAndDescription(
            title = title,
            description = description,
            enabled = true,
            modifier = Modifier.padding(start = RowInset, end = RowInset, top = 14.dp, bottom = 6.dp),
        )
        Column(Modifier.selectableGroup()) {
            options.forEach { option ->
                val isSelected = option.value == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelected(option.value) },
                        )
                        .padding(horizontal = RowInset - 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clearAndSetSemantics {},
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** A selectable choice for [RadioSettingGroup]. */
internal data class RadioOption<T>(val value: T, val label: String)

@Composable
private fun TitleAndDescription(
    title: String,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = RowTitleStyle(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}
