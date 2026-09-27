package com.bornomala.keyboard.settings.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bornomala.keyboard.settings.R
import com.bornomala.keyboard.settings.data.fonts.GoogleFont
import com.bornomala.keyboard.settings.data.fonts.GoogleFontCategory
import com.bornomala.keyboard.settings.data.fonts.GoogleFonts
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.settings.presentation.components.SettingsPage
import com.bornomala.keyboard.settings.presentation.components.SettingsSectionHeader
import com.bornomala.keyboard.settings.presentation.components.rememberKeyboardFont
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.LucideIcons
import com.bornomala.keyboard.theme.keyboardFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** Sample line each font is previewed with: the key labels are Latin letters and digits. */
private const val SAMPLE = "Aa Bb Cc 123 — the quick brown fox"

/**
 * At most this many preview fetches at once, so a fast fling doesn't queue hundreds of
 * blocking provider calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val PreviewDispatcher = Dispatchers.IO.limitedParallelism(4)

/**
 * Key-label font picker: the built-in fonts, the saved custom font, "Import font file", and —
 * when the phone has Google Play services — every Google Font in a searchable, lazily loaded
 * list. Each row is drawn in its own font; picking a Google Font downloads and saves it.
 */
@Composable
internal fun FontSettings(
    settings: Settings,
    callbacks: SettingsCallbacks,
    busyFont: String?,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val googleAvailable by produceState(initialValue = false) {
        value = withContext(Dispatchers.IO) { GoogleFonts.isAvailable(context) }
    }
    val catalog by produceState(initialValue = emptyList<GoogleFont>(), googleAvailable) {
        if (googleAvailable) value = withContext(Dispatchers.IO) { GoogleFonts.catalog(context) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<GoogleFontCategory?>(null) }
    val shown = remember(catalog, query, category) {
        val q = query.trim()
        catalog.filter { f ->
            (category == null || f.category == category) && (q.isEmpty() || f.family.contains(q, ignoreCase = true))
        }
    }
    // The system file picker; the font is copied in, so the original can be moved or deleted.
    val importFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(callbacks.onImportFont)
    }
    val customFamily = rememberKeyboardFont(settings.customFontStamp)
    val isCustom = settings.keyboardFont == KeyboardFont.CUSTOM

    SettingsPage(title = title, onBack = onBack, modifier = modifier, scrollable = false) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "h-phone") { SettingsSectionHeader(stringResource(R.string.settings_font_on_phone)) }
            item(key = "system") {
                FontRow(
                    name = KeyboardFont.SYSTEM.displayName,
                    family = null,
                    selected = settings.keyboardFont == KeyboardFont.SYSTEM,
                    onClick = { callbacks.onKeyboardFont(KeyboardFont.SYSTEM) },
                )
            }
            item(key = "mono") {
                FontRow(
                    name = KeyboardFont.JETBRAINS_MONO.displayName,
                    family = keyboardFontFamily(KeyboardFont.JETBRAINS_MONO),
                    selected = settings.keyboardFont == KeyboardFont.JETBRAINS_MONO,
                    onClick = { callbacks.onKeyboardFont(KeyboardFont.JETBRAINS_MONO) },
                )
            }
            if (settings.customFontStamp != 0L && customFamily != null) {
                item(key = "custom") {
                    FontRow(
                        name = settings.customFontName.ifEmpty { KeyboardFont.CUSTOM.displayName },
                        family = customFamily,
                        selected = isCustom,
                        onClick = { callbacks.onKeyboardFont(KeyboardFont.CUSTOM) },
                    )
                }
            }
            item(key = "import") {
                ImportRow(
                    busy = busyFont == IMPORTING_FONT,
                    onClick = {
                        importFont.launch(
                            arrayOf(
                                "font/*",
                                "application/x-font-ttf",
                                "application/x-font-otf",
                                "application/font-sfnt",
                                "application/vnd.ms-opentype",
                                "application/octet-stream",
                            ),
                        )
                    },
                )
            }
            if (googleAvailable) {
                item(key = "h-google") { SettingsSectionHeader(stringResource(R.string.settings_font_google)) }
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.settings_font_search)) },
                        leadingIcon = { Icon(LucideIcons.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                item(key = "chips") {
                    CategoryChips(selected = category, onSelect = { category = it })
                }
                items(shown, key = { "g-" + it.family }) { font ->
                    GoogleFontRow(
                        family = font.family,
                        selected = isCustom && settings.customFontName == font.family,
                        busy = busyFont == font.family,
                        onClick = { callbacks.onGoogleFont(font.family) },
                    )
                }
            }
        }
    }
}

/** Marker for [FontSettings]'s busy state while an imported file is being copied in. */
internal const val IMPORTING_FONT = "\u0000import"

@Composable
private fun CategoryChips(selected: GoogleFontCategory?, onSelect: (GoogleFontCategory?) -> Unit) {
    val labels = mapOf(
        null to R.string.settings_font_all,
        GoogleFontCategory.SANS to R.string.settings_font_sans,
        GoogleFontCategory.SERIF to R.string.settings_font_serif,
        GoogleFontCategory.DISPLAY to R.string.settings_font_display,
        GoogleFontCategory.HANDWRITING to R.string.settings_font_handwriting,
        GoogleFontCategory.MONO to R.string.settings_font_mono,
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(labels.keys.toList(), key = { it?.key ?: "all" }) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(stringResource(labels.getValue(category))) },
            )
        }
    }
}

@Composable
private fun GoogleFontRow(family: String, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    // Fetched only while the row is on screen; Play services caches each font after the first time.
    val fontFamily by produceState<FontFamily?>(initialValue = null, family) {
        value = withContext(PreviewDispatcher) { GoogleFonts.typeface(context, family)?.let { FontFamily(it) } }
    }
    FontRow(
        name = family,
        family = fontFamily,
        selected = selected,
        onClick = onClick,
        busy = busy,
        loading = fontFamily == null,
    )
}

/** A font choice: its name, and a sample line drawn in the font itself. */
@Composable
private fun FontRow(
    name: String,
    family: FontFamily?,
    selected: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false,
    loading: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.RadioButton, enabled = !busy, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = SAMPLE,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (loading) 0.3f else 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        when {
            busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            selected -> Icon(LucideIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ImportRow(busy: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, enabled = !busy, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LucideIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_font_import), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.settings_font_import_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}
