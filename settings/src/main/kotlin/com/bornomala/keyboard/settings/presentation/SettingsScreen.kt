package com.bornomala.keyboard.settings.presentation

import com.bornomala.keyboard.theme.LucideIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.keyboardColorsFor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bornomala.keyboard.ime.presentation.KeyboardConfiguratorPreview
import com.bornomala.keyboard.theme.BornomalaTheme
import com.bornomala.keyboard.theme.keyboardMetrics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bornomala.keyboard.core.result.Resource
import com.bornomala.keyboard.settings.R
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.settings.presentation.components.RadioOption
import com.bornomala.keyboard.settings.presentation.components.RadioSettingGroup
import com.bornomala.keyboard.settings.presentation.components.SettingsDivider
import com.bornomala.keyboard.settings.presentation.components.SettingsSectionHeader
import com.bornomala.keyboard.settings.presentation.components.SliderSettingRow
import com.bornomala.keyboard.settings.presentation.components.SwitchSettingRow
import com.bornomala.keyboard.theme.ThemeMode
import kotlin.math.roundToInt

/**
 * Top-level settings destination. Hosts the [SettingsViewModel] (Hilt-provided) and
 * renders the [Settings] snapshot once loaded.
 *
 * Stateless rendering is delegated to [SettingsContent] so it can be previewed and tested
 * without a ViewModel. While the first DataStore value is in flight, a progress indicator
 * is shown; on a load error the repository already falls back to defaults, so the success
 * branch covers the steady state.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    initialSection: String? = null,
    onSoftwareUpdate: () -> Unit = {},
    onCloudBackup: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = rememberCallbacks(viewModel, onSoftwareUpdate, onCloudBackup)

    when (val current = state) {
        is Resource.Loading -> SettingsScaffold(modifier) { LoadingState(it) }
        is Resource.Success -> SettingsContent(current.data, callbacks, modifier, initialSection)
        // The repository recovers read errors to defaults, so render defaults defensively.
        is Resource.Error -> SettingsContent(Settings.DEFAULTS, callbacks, modifier, initialSection)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "" },
        contentAlignment = Alignment.Center,
    ) {
        val cd = stringResource(R.string.settings_loading)
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = cd })
    }
}

/** Immutable bundle of every settings mutation, hoisted so [SettingsContent] is stateless. */
internal data class SettingsCallbacks(
    val onThemeMode: (ThemeMode) -> Unit,
    val onKeyboardTheme: (KeyboardTheme) -> Unit,
    val onKeyboardFont: (KeyboardFont) -> Unit,
    val onKeyBorder: (Boolean) -> Unit,
    val onHorizontalGapScale: (Float) -> Unit,
    val onVerticalGapScale: (Float) -> Unit,
    val onKeyLabelScale: (Float) -> Unit,
    val onSuggestionBarScale: (Float) -> Unit,
    val onBottomGapScale: (Float) -> Unit,
    val onKeyboardHeightScale: (Float) -> Unit,
    val onVibration: (Boolean) -> Unit,
    val onSound: (Boolean) -> Unit,
    val onNumberRow: (Boolean) -> Unit,
    val onSuggestions: (Boolean) -> Unit,
    val onAutoCorrect: (Boolean) -> Unit,
    val onBlockOffensiveWords: (Boolean) -> Unit,
    val onClipboard: (Boolean) -> Unit,
    val onAutoCap: (Boolean) -> Unit,
    val onDoubleSpace: (Boolean) -> Unit,
    val onBanglaAutoCommit: (Boolean) -> Unit,
    val onBanglaPhoneticSuggestions: (Boolean) -> Unit,
    val onLearnFromTyping: (Boolean) -> Unit,
    val onVolumeKeyCursorControl: (Boolean) -> Unit,
    val onResetToDefaults: () -> Unit,
    /** Delegated to the host ([SettingsActivity]) which launches [UpdatesActivity]. */
    val onSoftwareUpdate: () -> Unit = {},
    /** Delegated to the host which launches the cloud Backup & restore activity. */
    val onCloudBackup: () -> Unit = {},
)

@Composable
private fun rememberCallbacks(
    viewModel: SettingsViewModel,
    onSoftwareUpdate: () -> Unit,
    onCloudBackup: () -> Unit,
): SettingsCallbacks =
    remember(viewModel) {
        SettingsCallbacks(
            onThemeMode = viewModel::onThemeModeChange,
            onKeyboardTheme = viewModel::onKeyboardThemeChange,
            onKeyboardFont = viewModel::onKeyboardFontChange,
            onKeyBorder = viewModel::onKeyBorderChange,
            onHorizontalGapScale = viewModel::onHorizontalGapScaleChange,
            onVerticalGapScale = viewModel::onVerticalGapScaleChange,
            onKeyLabelScale = viewModel::onKeyLabelScaleChange,
            onSuggestionBarScale = viewModel::onSuggestionBarScaleChange,
            onBottomGapScale = viewModel::onBottomGapScaleChange,
            onKeyboardHeightScale = viewModel::onKeyboardHeightScaleChange,
            onVibration = viewModel::onKeyPressVibrationChange,
            onSound = viewModel::onKeyPressSoundChange,
            onNumberRow = viewModel::onNumberRowChange,
            onSuggestions = viewModel::onSuggestionsChange,
            onAutoCorrect = viewModel::onAutoCorrectChange,
            onBlockOffensiveWords = viewModel::onBlockOffensiveWordsChange,
            onClipboard = viewModel::onClipboardChange,
            onAutoCap = viewModel::onAutoCapitalizationChange,
            onDoubleSpace = viewModel::onDoubleSpacePeriodChange,
            onBanglaAutoCommit = viewModel::onBanglaAutoCommitChange,
            onBanglaPhoneticSuggestions = viewModel::onBanglaPhoneticSuggestionsChange,
            onLearnFromTyping = viewModel::onLearnFromTypingChange,
            onVolumeKeyCursorControl = viewModel::onVolumeKeyCursorControlChange,
            onResetToDefaults = viewModel::onResetToDefaults,
            onSoftwareUpdate = onSoftwareUpdate,
            onCloudBackup = onCloudBackup,
        )
    }

/** Top-level settings categories, each opening its own sub-screen. */
private enum class SettingsRoute(val titleRes: Int, val key: String?) {
    HOME(R.string.settings_title, null),
    APPEARANCE(R.string.settings_section_appearance, "appearance"),
    FEEDBACK(R.string.settings_section_feedback, "feedback"),
    PREFERENCES(R.string.settings_section_preferences, "preferences"),
    BANGLA(R.string.settings_section_bangla, "bangla"),
    ABOUT(R.string.settings_section_about, "about"),
    /** Delegated to the host activity via [SettingsCallbacks.onCloudBackup]. */
    BACKUP(R.string.settings_section_backup, "backup"),
    /** Delegated to the host activity via [SettingsCallbacks.onSoftwareUpdate]. */
    SOFTWARE_UPDATE(R.string.settings_section_software_update, "software_update");

    companion object {
        /** Maps an in-keyboard menu section key (see the IME's `SettingsSections`) to a route. */
        fun fromKey(key: String?): SettingsRoute? = key?.let { k ->
            // Legacy keys (Typing/Features were merged into Preferences) still deep-link correctly.
            when (k) {
                "typing", "features" -> PREFERENCES
                else -> entries.firstOrNull { it.key == k }
            }
        }
    }
}

/**
 * Settings host: a category list (HOME) that drills into a focused sub-screen per group,
 * instead of one long scroll. The top bar shows the active category with a back arrow; the
 * system back / arrow returns to HOME. Stateless (driven by [settings] + [callbacks]) so it
 * previews and tests without a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    settings: Settings,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
    initialSection: String? = null,
) {
    var route by rememberSaveable {
        mutableStateOf(SettingsRoute.fromKey(initialSection) ?: SettingsRoute.HOME)
    }
    var showResetDialog by remember { mutableStateOf(false) }

    if (route != SettingsRoute.HOME) BackHandler { route = SettingsRoute.HOME }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // HOME shows a large title (matching the grouped-card design); sub-screens use a
            // compact bar with a back arrow.
            if (route == SettingsRoute.HOME) {
                LargeTopAppBar(title = { Text(stringResource(route.titleRes)) })
            } else {
                TopAppBar(
                    title = { Text(stringResource(route.titleRes)) },
                    navigationIcon = {
                        IconButton(onClick = { route = SettingsRoute.HOME }) {
                            Icon(
                                imageVector = LucideIcons.ArrowLeft,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        val content = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
        when (route) {
            SettingsRoute.HOME -> SettingsHome(
                modifier = content,
                onOpen = { r ->
                    when (r) {
                        SettingsRoute.SOFTWARE_UPDATE -> callbacks.onSoftwareUpdate()
                        SettingsRoute.BACKUP -> callbacks.onCloudBackup()
                        else -> route = r
                    }
                },
                onReset = { showResetDialog = true },
            )
            SettingsRoute.APPEARANCE -> AppearanceSettings(settings, callbacks, content)
            SettingsRoute.FEEDBACK -> FeedbackSettings(settings, callbacks, content)
            SettingsRoute.PREFERENCES -> PreferencesSettings(settings, callbacks, content)
            SettingsRoute.BANGLA -> BanglaSettings(settings, callbacks, content)
            SettingsRoute.ABOUT -> Column(content) { SettingsCard { AboutSection() } }
            SettingsRoute.BACKUP -> Unit // handled by host via onCloudBackup callback
            SettingsRoute.SOFTWARE_UPDATE -> Unit // handled by host via onSoftwareUpdate callback
        }
    }

    if (showResetDialog) {
        ResetConfirmationDialog(
            onConfirm = {
                showResetDialog = false
                callbacks.onResetToDefaults()
            },
            onDismiss = { showResetDialog = false },
        )
    }
}

@Composable
private fun SettingsHome(
    modifier: Modifier,
    onOpen: (SettingsRoute) -> Unit,
    onReset: () -> Unit,
) {
    // Categories grouped into rounded cards (two per card), each row a muted icon + title +
    // subtitle with a hairline divider between rows in a card. Destructive Reset sits alone.
    val groups = listOf(
        listOf(
            CategoryItem(LucideIcons.Palette, stringResource(R.string.settings_section_appearance), "Theme and colors", SettingsRoute.APPEARANCE),
            CategoryItem(LucideIcons.Vibrate, stringResource(R.string.settings_section_feedback), "Vibration and sound", SettingsRoute.FEEDBACK),
        ),
        listOf(
            CategoryItem(LucideIcons.Keyboard, stringResource(R.string.settings_section_preferences), "Typing, font, size, suggestions", SettingsRoute.PREFERENCES),
            CategoryItem(LucideIcons.Languages, stringResource(R.string.settings_section_bangla), "Auto-commit and phonetic alternatives", SettingsRoute.BANGLA),
        ),
        listOf(
            CategoryItem(LucideIcons.Info, stringResource(R.string.settings_section_about), "Version, privacy, license", SettingsRoute.ABOUT),
            CategoryItem(LucideIcons.RefreshCw, stringResource(R.string.settings_section_backup), "Save your data to Google Drive", SettingsRoute.BACKUP),
            CategoryItem(LucideIcons.Download, stringResource(R.string.settings_section_software_update), "Check for and install app updates", SettingsRoute.SOFTWARE_UPDATE),
        ),
    )
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        groups.forEach { group ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                group.forEachIndexed { index, item ->
                    CategoryRow(item) { onOpen(item.route) }
                    if (index < group.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            ResetRow(onClick = onReset)
        }
    }
}

private data class CategoryItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val route: SettingsRoute,
)

@Composable
private fun CategoryRow(item: CategoryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettings(settings: Settings, callbacks: SettingsCallbacks, modifier: Modifier) {
    var showConfigurator by remember { mutableStateOf(false) }
    // "Try now" (SwiftKey-style): reveals a focused text field that summons the real keyboard,
    // so the user can type and watch theme changes apply live. While it is active, tapping a
    // theme tile applies the theme immediately instead of opening the configurator sheet (the
    // sheet would cover the live keyboard).
    var tryNow by rememberSaveable { mutableStateOf(false) }
    if (tryNow) BackHandler { tryNow = false }

    Box(Modifier.fillMaxSize()) {
        // imePadding so the scroll content (incl. the height / bottom-gap sliders) can scroll
        // above the keyboard when "Try now" is open — the activity is edge-to-edge, so the
        // keyboard is an inset, not a window resize.
        Column(modifier.imePadding()) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    KeyboardTheme.entries.chunked(3).forEach { rowThemes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            rowThemes.forEach { theme ->
                                val preview = keyboardColorsFor(theme, systemDark)
                                ThemeSwatch(
                                    name = theme.displayName,
                                    tray = preview.keyboardBackground,
                                    key = preview.keyBackground,
                                    accent = preview.accentKeyBackground,
                                    spacebarBar = preview.keyContent.copy(alpha = 0.35f),
                                    selected = theme == settings.keyboardTheme,
                                    onClick = {
                                        callbacks.onKeyboardTheme(theme)
                                        if (!tryNow) showConfigurator = true
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - rowThemes.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            // Breathing room so the floating button never hides the last card.
            Spacer(Modifier.height(88.dp))
        }

        if (tryNow) {
            TryNowField(
                onClose = { tryNow = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            ExtendedFloatingActionButton(
                onClick = { tryNow = true },
                icon = { Icon(LucideIcons.Keyboard, contentDescription = null) },
                text = { Text(stringResource(R.string.settings_try_now)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp),
            )
        }
    }

    if (showConfigurator) {
        ConfiguratorSheet(settings, callbacks, onDismiss = { showConfigurator = false })
    }
}

/**
 * The SwiftKey-style "try out your setup" bar. An auto-focused text field pinned above the
 * IME (via [imePadding]); focusing it makes the system show the active keyboard — Bornomala,
 * when it is the selected IME — so theme/gap/font changes (persisted to DataStore and observed
 * by the running IME) are reflected live as the user types.
 */
@Composable
private fun TryNowField(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    var text by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // The activity is edge-to-edge, so the keyboard is reported as an inset rather than a
    // window resize: imePadding lifts the field to sit directly above the keyboard.
    Surface(modifier = modifier.fillMaxWidth().imePadding(), tonalElevation = 3.dp) {
        Column {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.settings_try_now_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                IconButton(onClick = onClose) {
                    Icon(LucideIcons.X, contentDescription = stringResource(R.string.settings_back))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfiguratorSheet(
    settings: Settings,
    callbacks: SettingsCallbacks,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // Fixed-height frame so dragging the size sliders never resizes the sheet.
            // Inside it we render the *real* keyboard composable wrapped in a BornomalaTheme
            // whose metrics come from the live settings, so the preview matches the actual
            // keyboard exactly and reacts to every slider/switch without a separate mock.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                BornomalaTheme(
                    theme = settings.keyboardTheme,
                    font = settings.keyboardFont,
                    metrics = keyboardMetrics(
                        horizontalGapScale = settings.horizontalGapScale,
                        verticalGapScale = settings.verticalGapScale,
                        keyLabelScale = settings.keyLabelScale,
                        suggestionBarScale = settings.suggestionBarScale,
                        bottomGapScale = settings.bottomGapScale,
                        keyBorder = settings.keyBorder,
                    ),
                ) {
                    KeyboardConfiguratorPreview(Modifier.fillMaxSize())
                }
            }
            SwitchSettingRow(
                title = "Key border",
                description = "Draw a hairline around each key.",
                checked = settings.keyBorder,
                onCheckedChange = callbacks.onKeyBorder,
            )
            Text(
                text = "Key gaps & font sizes",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            )
            ScaleSlider("Vertical gap", "", settings.verticalGapScale, callbacks.onVerticalGapScale)
            ScaleSlider("Horizontal gap", "", settings.horizontalGapScale, callbacks.onHorizontalGapScale)
            ScaleSlider("Key label size", "", settings.keyLabelScale, callbacks.onKeyLabelScale)
            ScaleSlider("Suggestion bar size", "", settings.suggestionBarScale, callbacks.onSuggestionBarScale)
        }
    }
}

/** Wraps a group of setting rows in a rounded card, matching the home menu's grouping. */
@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun ScaleSlider(title: String, description: String, value: Float, onChange: (Float) -> Unit) {
    val percent = (value * 100f).roundToInt()
    SliderSettingRow(
        title = title,
        description = description,
        valueLabel = "$percent%",
        sliderContentDescription = "$title, $percent percent",
        value = value,
        valueRange = 0.5f..1.5f,
        steps = 9,
        onValueChange = onChange,
    )
}

/** Gboard-style theme tile: a mini keyboard preview (tray + spacebar bar + accent enter dot). */
@Composable
private fun ThemeSwatch(
    name: String,
    tray: Color,
    key: Color,
    accent: Color,
    spacebarBar: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tray)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(12.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(spacebarBar),
                )
                androidx.compose.foundation.layout.Box(
                    Modifier.size(12.dp).clip(CircleShape).background(accent),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedbackSettings(settings: Settings, callbacks: SettingsCallbacks, modifier: Modifier) {
    Column(modifier) {
        SettingsCard {
            SwitchSettingRow(
                title = stringResource(R.string.settings_vibration),
                description = stringResource(R.string.settings_vibration_desc),
                checked = settings.keyPressVibration,
                onCheckedChange = callbacks.onVibration,
            )
            SwitchSettingRow(
                title = stringResource(R.string.settings_sound),
                description = stringResource(R.string.settings_sound_desc),
                checked = settings.keyPressSound,
                onCheckedChange = callbacks.onSound,
            )
        }
    }
}

/**
 * Preferences = the former Typing + Features screens merged into one. Two labelled cards keep
 * the original grouping (typing behaviour, then suggestion/learning/clipboard features).
 */
@Composable
private fun PreferencesSettings(settings: Settings, callbacks: SettingsCallbacks, modifier: Modifier) {
    Column(modifier) {
        SectionLabel(stringResource(R.string.settings_section_typing))
        SettingsCard {
            SwitchSettingRow(
                title = stringResource(R.string.settings_auto_cap),
                description = stringResource(R.string.settings_auto_cap_desc),
                checked = settings.autoCapitalization,
                onCheckedChange = callbacks.onAutoCap,
            )
            SwitchSettingRow(
                title = stringResource(R.string.settings_double_space),
                description = stringResource(R.string.settings_double_space_desc),
                checked = settings.doubleSpacePeriod,
                onCheckedChange = callbacks.onDoubleSpace,
            )
            SwitchSettingRow(
                title = stringResource(R.string.settings_number_row),
                description = stringResource(R.string.settings_number_row_desc),
                checked = settings.numberRowEnabled,
                onCheckedChange = callbacks.onNumberRow,
            )
            SwitchSettingRow(
                title = stringResource(R.string.settings_volume_cursor),
                description = stringResource(R.string.settings_volume_cursor_desc),
                checked = settings.volumeKeyCursorControl,
                onCheckedChange = callbacks.onVolumeKeyCursorControl,
            )
        }

        SectionLabel(stringResource(R.string.settings_section_features))
        SettingsCard {
            SwitchSettingRow(
                title = stringResource(R.string.settings_suggestions),
                description = stringResource(R.string.settings_suggestions_desc),
                checked = settings.suggestionsEnabled,
                onCheckedChange = callbacks.onSuggestions,
            )
            SwitchSettingRow(
                title = stringResource(R.string.settings_auto_correct),
                description = stringResource(R.string.settings_auto_correct_desc),
                checked = settings.autoCorrectEnabled,
                enabled = settings.suggestionsEnabled,
                onCheckedChange = callbacks.onAutoCorrect,
            )
            SwitchSettingRow(
                title = stringResource(R.string.settings_block_offensive),
                description = stringResource(R.string.settings_block_offensive_desc),
                checked = settings.blockOffensiveWords,
                enabled = settings.suggestionsEnabled,
                onCheckedChange = callbacks.onBlockOffensiveWords,
            )
            SwitchSettingRow(
                title = stringResource(R.string.settings_learn_typing),
                description = stringResource(R.string.settings_learn_typing_desc),
                checked = settings.learnFromTyping,
                enabled = settings.suggestionsEnabled,
                onCheckedChange = callbacks.onLearnFromTyping,
            )
            SwitchSettingRow(
                title = stringResource(R.string.settings_clipboard),
                description = stringResource(R.string.settings_clipboard_desc),
                checked = settings.clipboardEnabled,
                onCheckedChange = callbacks.onClipboard,
            )
        }

        SectionLabel(stringResource(R.string.settings_section_layout))
        SettingsCard {
            RadioSettingGroup(
                title = stringResource(R.string.settings_font),
                description = stringResource(R.string.settings_font_desc),
                options = KeyboardFont.entries.map { RadioOption(it, it.displayName) },
                selected = settings.keyboardFont,
                onSelected = callbacks.onKeyboardFont,
            )
        }
        SettingsCard {
            HeightSlider(
                scale = settings.keyboardHeightScale,
                onScaleChange = callbacks.onKeyboardHeightScale,
            )
            BottomGapSlider(
                scale = settings.bottomGapScale,
                onScaleChange = callbacks.onBottomGapScale,
            )
        }
        // Breathing room at the end of the scroll.
        Spacer(Modifier.height(24.dp))
    }
}

/** Small group label above a [SettingsCard], matching the Appearance screen's section headers. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun BanglaSettings(settings: Settings, callbacks: SettingsCallbacks, modifier: Modifier) {
    Column(modifier) {
        SettingsCard {
            SwitchSettingRow(
                title = stringResource(R.string.settings_bangla_auto_commit),
                description = stringResource(R.string.settings_bangla_auto_commit_desc),
                checked = settings.banglaAutoCommit,
                onCheckedChange = callbacks.onBanglaAutoCommit,
            )
            SwitchSettingRow(
                title = stringResource(R.string.settings_bangla_phonetic_suggestions),
                description = stringResource(R.string.settings_bangla_phonetic_suggestions_desc),
                checked = settings.banglaPhoneticSuggestions,
                enabled = settings.suggestionsEnabled,
                onCheckedChange = callbacks.onBanglaPhoneticSuggestions,
            )
        }
    }
}

@Composable
private fun HeightSlider(
    scale: Float,
    onScaleChange: (Float) -> Unit,
) {
    val percent = (scale * 100f).roundToInt()
    SliderSettingRow(
        title = stringResource(R.string.settings_keyboard_height),
        description = stringResource(R.string.settings_keyboard_height_desc),
        valueLabel = stringResource(R.string.settings_keyboard_height_value, percent),
        sliderContentDescription = stringResource(R.string.settings_height_slider_cd, percent),
        value = scale,
        valueRange = Settings.MIN_KEYBOARD_HEIGHT_SCALE..Settings.MAX_KEYBOARD_HEIGHT_SCALE,
        // 5% increments across the 75%..140% range.
        steps = 12,
        onValueChange = onScaleChange,
    )
}

/** Adjusts the gap below the last key row (above the gesture/navigation bar). */
@Composable
private fun BottomGapSlider(
    scale: Float,
    onScaleChange: (Float) -> Unit,
) {
    // The gap is stored as a multiplier of the 24dp base; show it as an absolute dp value
    // (0–72dp, default 24dp) since that reads more clearly than a percentage. Stops every 2dp.
    val dp = (scale * BOTTOM_GAP_BASE_DP).roundToInt()
    SliderSettingRow(
        title = "Bottom gap",
        description = "Space below the keyboard, above the navigation bar.",
        valueLabel = "${dp}dp",
        sliderContentDescription = "Bottom gap, $dp dp",
        value = scale,
        valueRange = 0f..3f,
        steps = 35,
        onValueChange = onScaleChange,
    )
}

/** Base bottom-gap height in dp at 100% (mirrors KeyboardDimens.keyboardBottomGap). */
private const val BOTTOM_GAP_BASE_DP = 24

/** A clickable (non-toggle) destructive action row matching the settings row look. */
@Composable
private fun ResetRow(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_reset),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.settings_reset_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** App identity, version (read from the installed package), and the privacy/credits notes. */
@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_about_app),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_about_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_about_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_about_avro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_about_licenses),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_about_warranty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResetConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
        text = { Text(stringResource(R.string.settings_reset_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_reset_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
