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
import com.bornomala.keyboard.theme.KeyboardDimens
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
import androidx.compose.foundation.horizontalScroll
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
import com.bornomala.keyboard.settings.presentation.components.SettingsActionRow
import com.bornomala.keyboard.settings.presentation.components.SettingsNavRow
import com.bornomala.keyboard.settings.presentation.components.HeightHandle
import com.bornomala.keyboard.settings.presentation.components.ResizableKeyboardPreview
import com.bornomala.keyboard.settings.presentation.components.SettingsPage
import com.bornomala.keyboard.settings.presentation.components.rememberKeyboardPhoto
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
    val busyFont by viewModel.fontBusy.collectAsStateWithLifecycle()
    val callbacks = rememberCallbacks(viewModel, onSoftwareUpdate, onCloudBackup)

    when (val current = state) {
        is Resource.Loading -> SettingsScaffold(modifier) { LoadingState(it) }
        is Resource.Success -> SettingsContent(current.data, callbacks, modifier, initialSection, busyFont)
        // The repository recovers read errors to defaults, so render defaults defensively.
        is Resource.Error -> SettingsContent(Settings.DEFAULTS, callbacks, modifier, initialSection, busyFont)
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
    val onBackgroundPhotoPicked: (android.net.Uri) -> Unit,
    val onGoogleFont: (String) -> Unit,
    val onImportFont: (android.net.Uri) -> Unit,
    val onBackgroundDim: (Float) -> Unit,
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
            onBackgroundPhotoPicked = viewModel::onBackgroundPhotoPicked,
            onGoogleFont = viewModel::onGoogleFontChosen,
            onImportFont = viewModel::onFontImported,
            onBackgroundDim = viewModel::onBackgroundDimChange,
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

/** Top-level settings categories, each opening its own sub-screen (Gboard's grouping). */
private enum class SettingsRoute(val titleRes: Int, val key: String?) {
    HOME(R.string.settings_title, null),
    LANGUAGES(R.string.settings_section_languages, "bangla"),
    PREFERENCES(R.string.settings_section_preferences, "preferences"),
    THEME(R.string.settings_theme, "appearance"),
    /** Key-label font picker, opened from Preferences (back returns there). */
    FONT(R.string.settings_font, "font"),
    CORRECTIONS(R.string.settings_section_corrections, "corrections"),
    CLIPBOARD(R.string.settings_section_clipboard, "clipboard"),
    ABOUT(R.string.settings_section_about, "about"),
    /** Delegated to the host activity via [SettingsCallbacks.onCloudBackup]. */
    BACKUP(R.string.settings_section_backup, "backup"),
    /** Delegated to the host activity via [SettingsCallbacks.onSoftwareUpdate]. */
    SOFTWARE_UPDATE(R.string.settings_section_software_update, "software_update");

    companion object {
        /** Maps an in-keyboard menu section key (see the IME's `SettingsSections`) to a route. */
        fun fromKey(key: String?): SettingsRoute? = key?.let { k ->
            // Older keys still deep-link: key feedback and typing now live in Preferences,
            // the former Features group is Corrections & suggestions.
            when (k) {
                "feedback", "typing" -> PREFERENCES
                "features" -> CORRECTIONS
                else -> entries.firstOrNull { it.key == k }
            }
        }
    }
}

/**
 * Settings host: a Gboard-style category list (HOME) that drills into one flat sub-screen per
 * group. Each screen has a large collapsing title; system back / the arrow returns to HOME.
 * Stateless (driven by [settings] + [callbacks]) so it previews and tests without a ViewModel.
 */
@Composable
internal fun SettingsContent(
    settings: Settings,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
    initialSection: String? = null,
    busyFont: String? = null,
) {
    var route by rememberSaveable {
        mutableStateOf(SettingsRoute.fromKey(initialSection) ?: SettingsRoute.HOME)
    }
    var showResetDialog by remember { mutableStateOf(false) }

    // Sub-screens return to HOME, except the font picker, which returns to Preferences.
    val parent = if (route == SettingsRoute.FONT) SettingsRoute.PREFERENCES else SettingsRoute.HOME
    if (route != SettingsRoute.HOME) BackHandler { route = parent }
    val back = { route = parent }
    val title = stringResource(route.titleRes)

    when (route) {
        SettingsRoute.HOME -> SettingsHome(
            settings = settings,
            modifier = modifier,
            onOpen = { r ->
                when (r) {
                    SettingsRoute.SOFTWARE_UPDATE -> callbacks.onSoftwareUpdate()
                    SettingsRoute.BACKUP -> callbacks.onCloudBackup()
                    else -> route = r
                }
            },
            onReset = { showResetDialog = true },
        )
        SettingsRoute.THEME -> ThemeSettings(settings, callbacks, title, back, modifier)
        SettingsRoute.LANGUAGES -> SettingsPage(title, back, modifier) { LanguagesSettings(settings, callbacks) }
        SettingsRoute.PREFERENCES -> SettingsPage(title, back, modifier) {
            PreferencesSettings(settings, callbacks, onOpenFonts = { route = SettingsRoute.FONT })
        }
        SettingsRoute.FONT -> FontSettings(settings, callbacks, busyFont, title, back, modifier)
        SettingsRoute.CORRECTIONS -> SettingsPage(title, back, modifier) { CorrectionsSettings(settings, callbacks) }
        SettingsRoute.CLIPBOARD -> SettingsPage(title, back, modifier) { ClipboardSettings(settings, callbacks) }
        SettingsRoute.ABOUT -> SettingsPage(title, back, modifier) { AboutSection() }
        SettingsRoute.BACKUP, SettingsRoute.SOFTWARE_UPDATE -> Unit // handled by the host
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
    settings: Settings,
    modifier: Modifier,
    onOpen: (SettingsRoute) -> Unit,
    onReset: () -> Unit,
) {
    SettingsPage(title = stringResource(R.string.settings_title), onBack = null, modifier = modifier) {
        SettingsNavRow(
            icon = LucideIcons.Globe,
            title = stringResource(R.string.settings_section_languages),
            summary = stringResource(R.string.settings_languages_summary),
            onClick = { onOpen(SettingsRoute.LANGUAGES) },
        )
        SettingsNavRow(LucideIcons.Settings, stringResource(R.string.settings_section_preferences), onClick = { onOpen(SettingsRoute.PREFERENCES) })
        SettingsNavRow(
            icon = LucideIcons.Palette,
            title = stringResource(R.string.settings_theme),
            summary = settings.keyboardTheme.displayName,
            onClick = { onOpen(SettingsRoute.THEME) },
        )
        SettingsNavRow(LucideIcons.Lightbulb, stringResource(R.string.settings_section_corrections), onClick = { onOpen(SettingsRoute.CORRECTIONS) })
        SettingsNavRow(LucideIcons.ClipboardList, stringResource(R.string.settings_section_clipboard), onClick = { onOpen(SettingsRoute.CLIPBOARD) })
        SettingsNavRow(LucideIcons.RefreshCw, stringResource(R.string.settings_section_backup), onClick = { onOpen(SettingsRoute.BACKUP) })
        SettingsNavRow(LucideIcons.Download, stringResource(R.string.settings_section_software_update), onClick = { onOpen(SettingsRoute.SOFTWARE_UPDATE) })
        SettingsNavRow(LucideIcons.Info, stringResource(R.string.settings_section_about), onClick = { onOpen(SettingsRoute.ABOUT) })
        SettingsActionRow(
            title = stringResource(R.string.settings_reset),
            summary = stringResource(R.string.settings_reset_desc),
            onClick = onReset,
            titleColor = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Theme & layout: the real keyboard pinned at the top, rendered from the live settings so every
 * change below shows immediately, with a drag handle for its height; under it one scrolling row
 * of themes and the size/gap controls.
 */
@Composable
private fun ThemeSettings(
    settings: Settings,
    callbacks: SettingsCallbacks,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    // The system photo picker: no storage permission, and only the chosen image is shared.
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(callbacks.onBackgroundPhotoPicked) }
    val launchPicker = {
        pickPhoto.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
    }
    val photoActive = settings.keyboardTheme == KeyboardTheme.IMAGE

    SettingsPage(
        title = title,
        onBack = onBack,
        modifier = modifier,
        pinned = { KeyboardPreviewBand(settings, callbacks.onKeyboardHeightScale) },
    ) {
        SettingsSectionHeader(stringResource(R.string.settings_theme))
        ThemeRow(
            selected = settings.keyboardTheme,
            photoStamp = settings.backgroundImageStamp,
            onSelect = callbacks.onKeyboardTheme,
            onPhoto = {
                // No photo yet, or the photo theme is already on: pick one. Otherwise switch
                // back to the photo chosen earlier.
                if (settings.backgroundImageStamp == 0L || photoActive) {
                    launchPicker()
                } else {
                    callbacks.onKeyboardTheme(KeyboardTheme.IMAGE)
                }
            },
        )
        if (photoActive) {
            SettingsSectionHeader(stringResource(R.string.settings_section_photo))
            SettingsActionRow(
                title = stringResource(R.string.settings_photo_change),
                summary = stringResource(R.string.settings_photo_change_desc),
                onClick = launchPicker,
            )
            val dimPercent = (settings.backgroundDim * 100f).roundToInt()
            SliderSettingRow(
                title = stringResource(R.string.settings_photo_dim),
                description = stringResource(R.string.settings_photo_dim_desc),
                valueLabel = "$dimPercent%",
                sliderContentDescription = stringResource(R.string.settings_photo_dim) + ", $dimPercent percent",
                value = settings.backgroundDim,
                valueRange = 0f..Settings.MAX_BACKGROUND_DIM,
                steps = 15,
                onValueChange = callbacks.onBackgroundDim,
            )
        }

        SettingsSectionHeader(stringResource(R.string.settings_section_keys))
        SwitchSettingRow(
            title = stringResource(R.string.settings_key_border),
            description = stringResource(R.string.settings_key_border_desc),
            checked = settings.keyBorder,
            onCheckedChange = callbacks.onKeyBorder,
        )
        ScaleSlider(stringResource(R.string.settings_key_label_size), settings.keyLabelScale, callbacks.onKeyLabelScale)
        ScaleSlider(stringResource(R.string.settings_suggestion_bar_size), settings.suggestionBarScale, callbacks.onSuggestionBarScale)

        SettingsSectionHeader(stringResource(R.string.settings_section_gaps))
        ScaleSlider(stringResource(R.string.settings_vertical_gap), settings.verticalGapScale, callbacks.onVerticalGapScale)
        ScaleSlider(stringResource(R.string.settings_horizontal_gap), settings.horizontalGapScale, callbacks.onHorizontalGapScale)
    }
}

/**
 * The real keyboard in the chosen theme, font, metrics, height and number row, with a handle on
 * its bottom edge: drag down for taller keys, up for shorter.
 */
@Composable
internal fun KeyboardPreviewBand(settings: Settings, onHeightChange: (Float) -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        ResizableKeyboardPreview(
            settings = settings,
            onHeightChange = onHeightChange,
            modifier = Modifier.fillMaxWidth(),
            handle = HeightHandle.BOTTOM,
            shape = RoundedCornerShape(14.dp),
        )
    }
}

/**
 * Every colour theme in one horizontally scrolling row, opened on the current one, then the
 * photo tile: the user's photo once chosen, otherwise an "add photo" tile.
 */
@Composable
private fun ThemeRow(
    selected: KeyboardTheme,
    photoStamp: Long,
    onSelect: (KeyboardTheme) -> Unit,
    onPhoto: () -> Unit,
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(Unit) {
        val index = KeyboardTheme.entries.indexOf(selected)
        scrollState.scrollTo(with(density) { ((ThemeTileWidth + ThemeTileGap) * index).roundToPx() })
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ThemeTileGap),
    ) {
        KeyboardTheme.entries.filter { it != KeyboardTheme.IMAGE }.forEach { theme ->
            val preview = keyboardColorsFor(theme, systemDark)
            ThemeSwatch(
                name = theme.displayName,
                tray = preview.keyboardBackground,
                key = preview.keyBackground,
                accent = preview.accentKeyBackground,
                spacebarBar = preview.keyContent.copy(alpha = 0.35f),
                selected = theme == selected,
                onClick = { onSelect(theme) },
                modifier = Modifier.width(ThemeTileWidth),
            )
        }
        PhotoTile(
            stamp = photoStamp,
            selected = selected == KeyboardTheme.IMAGE,
            onClick = onPhoto,
            modifier = Modifier.width(ThemeTileWidth),
        )
    }
}

/** The photo theme's tile: a thumbnail of the chosen photo, or an "add photo" prompt. */
@Composable
private fun PhotoTile(stamp: Long, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val thumbnail = rememberKeyboardPhoto(stamp)
    val shape = RoundedCornerShape(14.dp)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = shape,
                )
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(shape),
                )
            } else {
                Icon(
                    LucideIcons.ImagePlus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(if (thumbnail != null) R.string.settings_photo else R.string.settings_photo_add),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ThemeTileWidth = 104.dp
private val ThemeTileGap = 12.dp

@Composable
private fun ScaleSlider(title: String, value: Float, onChange: (Float) -> Unit) {
    val percent = (value * 100f).roundToInt()
    SliderSettingRow(
        title = title,
        description = "",
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

/** Bangla transliteration options (the only language with settings of its own). */
@Composable
private fun LanguagesSettings(settings: Settings, callbacks: SettingsCallbacks) {
    SettingsSectionHeader(stringResource(R.string.settings_section_bangla))
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

/** Keys, key-press feedback, and font & size — as Gboard's Preferences groups them. */
@Composable
private fun PreferencesSettings(settings: Settings, callbacks: SettingsCallbacks, onOpenFonts: () -> Unit) {
    SettingsSectionHeader(stringResource(R.string.settings_section_keys))
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

    SettingsSectionHeader(stringResource(R.string.settings_section_key_press))
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

    SettingsSectionHeader(stringResource(R.string.settings_section_layout))
    SettingsNavRow(
        icon = LucideIcons.Languages,
        title = stringResource(R.string.settings_font),
        summary = if (settings.keyboardFont == KeyboardFont.CUSTOM) {
            settings.customFontName.ifEmpty { KeyboardFont.CUSTOM.displayName }
        } else {
            settings.keyboardFont.displayName
        },
        onClick = onOpenFonts,
    )
    HeightSlider(
        scale = settings.keyboardHeightScale,
        onScaleChange = callbacks.onKeyboardHeightScale,
    )
    BottomGapSlider(
        scale = settings.bottomGapScale,
        onScaleChange = callbacks.onBottomGapScale,
    )
}

/** Suggestions, auto-correction and the typing aids Gboard files under the same heading. */
@Composable
private fun CorrectionsSettings(settings: Settings, callbacks: SettingsCallbacks) {
    SettingsSectionHeader(stringResource(R.string.settings_section_suggestions))
    SwitchSettingRow(
        title = stringResource(R.string.settings_suggestions),
        description = stringResource(R.string.settings_suggestions_desc),
        checked = settings.suggestionsEnabled,
        onCheckedChange = callbacks.onSuggestions,
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

    SettingsSectionHeader(stringResource(R.string.settings_section_corrections_group))
    SwitchSettingRow(
        title = stringResource(R.string.settings_auto_correct),
        description = stringResource(R.string.settings_auto_correct_desc),
        checked = settings.autoCorrectEnabled,
        enabled = settings.suggestionsEnabled,
        onCheckedChange = callbacks.onAutoCorrect,
    )
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
}

@Composable
private fun ClipboardSettings(settings: Settings, callbacks: SettingsCallbacks) {
    SwitchSettingRow(
        title = stringResource(R.string.settings_clipboard),
        description = stringResource(R.string.settings_clipboard_desc),
        checked = settings.clipboardEnabled,
        onCheckedChange = callbacks.onClipboard,
    )
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
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
