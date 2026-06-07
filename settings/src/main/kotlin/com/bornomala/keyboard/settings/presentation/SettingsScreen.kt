package com.bornomala.keyboard.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScaffold(modifier = modifier) { innerModifier ->
        when (val current = state) {
            is Resource.Loading -> LoadingState(innerModifier)
            is Resource.Success -> SettingsContent(
                settings = current.data,
                callbacks = rememberCallbacks(viewModel),
                modifier = innerModifier,
            )

            is Resource.Error -> {
                // The repository recovers read errors to defaults, so an Error state is not
                // expected on the read flow; render defaults defensively rather than blanking.
                SettingsContent(
                    settings = Settings.DEFAULTS,
                    callbacks = rememberCallbacks(viewModel),
                    modifier = innerModifier,
                )
            }
        }
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
    val onHighContrast: (Boolean) -> Unit,
    val onKeyboardHeightScale: (Float) -> Unit,
    val onVibration: (Boolean) -> Unit,
    val onSound: (Boolean) -> Unit,
    val onNumberRow: (Boolean) -> Unit,
    val onSuggestions: (Boolean) -> Unit,
    val onClipboard: (Boolean) -> Unit,
    val onAutoCap: (Boolean) -> Unit,
    val onDoubleSpace: (Boolean) -> Unit,
    val onBanglaAutoCommit: (Boolean) -> Unit,
    val onBanglaPhoneticSuggestions: (Boolean) -> Unit,
    val onLearnFromTyping: (Boolean) -> Unit,
    val onResetToDefaults: () -> Unit,
)

@Composable
private fun rememberCallbacks(viewModel: SettingsViewModel): SettingsCallbacks =
    remember(viewModel) {
        SettingsCallbacks(
            onThemeMode = viewModel::onThemeModeChange,
            onHighContrast = viewModel::onHighContrastChange,
            onKeyboardHeightScale = viewModel::onKeyboardHeightScaleChange,
            onVibration = viewModel::onKeyPressVibrationChange,
            onSound = viewModel::onKeyPressSoundChange,
            onNumberRow = viewModel::onNumberRowChange,
            onSuggestions = viewModel::onSuggestionsChange,
            onClipboard = viewModel::onClipboardChange,
            onAutoCap = viewModel::onAutoCapitalizationChange,
            onDoubleSpace = viewModel::onDoubleSpacePeriodChange,
            onBanglaAutoCommit = viewModel::onBanglaAutoCommitChange,
            onBanglaPhoneticSuggestions = viewModel::onBanglaPhoneticSuggestionsChange,
            onLearnFromTyping = viewModel::onLearnFromTypingChange,
            onResetToDefaults = viewModel::onResetToDefaults,
        )
    }

@Composable
internal fun SettingsContent(
    settings: Settings,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
) {
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Appearance
        SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
        RadioSettingGroup(
            title = stringResource(R.string.settings_theme),
            description = stringResource(R.string.settings_theme_desc),
            options = listOf(
                RadioOption(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light)),
                RadioOption(ThemeMode.DARK, stringResource(R.string.settings_theme_dark)),
                RadioOption(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system)),
            ),
            selected = settings.themeMode,
            onSelected = callbacks.onThemeMode,
        )
        SwitchSettingRow(
            title = stringResource(R.string.settings_high_contrast),
            description = stringResource(R.string.settings_high_contrast_desc),
            checked = settings.highContrast,
            onCheckedChange = callbacks.onHighContrast,
        )
        HeightSlider(
            scale = settings.keyboardHeightScale,
            onScaleChange = callbacks.onKeyboardHeightScale,
        )
        SettingsDivider()

        // Feedback
        SettingsSectionHeader(stringResource(R.string.settings_section_feedback))
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
        SettingsDivider()

        // Typing
        SettingsSectionHeader(stringResource(R.string.settings_section_typing))
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
        SettingsDivider()

        // Features
        SettingsSectionHeader(stringResource(R.string.settings_section_features))
        SwitchSettingRow(
            title = stringResource(R.string.settings_suggestions),
            description = stringResource(R.string.settings_suggestions_desc),
            checked = settings.suggestionsEnabled,
            onCheckedChange = callbacks.onSuggestions,
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
        SettingsDivider()

        // Bangla
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
        SettingsDivider()

        // Reset
        SettingsSectionHeader(stringResource(R.string.settings_section_accessibility))
        ResetRow(onClick = { showResetDialog = true })
        SettingsDivider()

        // About
        SettingsSectionHeader(stringResource(R.string.settings_section_about))
        AboutSection()
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
