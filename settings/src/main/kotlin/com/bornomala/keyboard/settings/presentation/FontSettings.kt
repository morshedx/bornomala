package com.bornomala.keyboard.settings.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bornomala.keyboard.ime.presentation.KeyboardConfiguratorPreview
import com.bornomala.keyboard.settings.R
import com.bornomala.keyboard.settings.data.fonts.GoogleFont
import com.bornomala.keyboard.settings.data.fonts.GoogleFontCategory
import com.bornomala.keyboard.settings.data.fonts.GoogleFonts
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.settings.presentation.components.SettingsPage
import com.bornomala.keyboard.settings.presentation.components.rememberKeyboardFont
import com.bornomala.keyboard.settings.presentation.components.rememberKeyboardPhoto
import com.bornomala.keyboard.theme.BornomalaTheme
import com.bornomala.keyboard.theme.KeyboardBackground
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.LocalKeyboardBackground
import com.bornomala.keyboard.theme.LucideIcons
import com.bornomala.keyboard.theme.keyboardFontFamily
import com.bornomala.keyboard.theme.keyboardMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * At most this many preview fetches at once, so a fast fling doesn't queue hundreds of
 * blocking provider calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val PreviewDispatcher = Dispatchers.IO.limitedParallelism(4)

/** Marker for [FontSettings]'s busy state while an imported file is being copied in. */
internal const val IMPORTING_FONT = "\u0000import"

private val CardShape = RoundedCornerShape(20.dp)

/** MIME types offered by the import picker; file managers label fonts inconsistently. */
private val FontMimeTypes = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/font-sfnt",
    "application/vnd.ms-opentype",
    "application/octet-stream",
)

/**
 * Key-label font picker. A live keyboard pinned on top shows the chosen font on real keys;
 * below it, a grid of font cards — each a large "Aa" drawn in that font — with the phone's
 * fonts and an Import card first, then (only when Google Play services is present) every
 * Google Font with search and category filters, previewed lazily as the grid scrolls.
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

    SettingsPage(
        title = title,
        onBack = onBack,
        modifier = modifier,
        pinned = { FontPreviewBand(settings, customFamily) },
        scrollable = false,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            header("h-phone") { GroupTitle(stringResource(R.string.settings_font_on_phone)) }
            item(key = "system") {
                FontCard(
                    name = KeyboardFont.SYSTEM.displayName,
                    family = null,
                    selected = settings.keyboardFont == KeyboardFont.SYSTEM,
                    onClick = { callbacks.onKeyboardFont(KeyboardFont.SYSTEM) },
                )
            }
            item(key = "mono") {
                FontCard(
                    name = KeyboardFont.JETBRAINS_MONO.displayName,
                    family = keyboardFontFamily(KeyboardFont.JETBRAINS_MONO),
                    selected = settings.keyboardFont == KeyboardFont.JETBRAINS_MONO,
                    onClick = { callbacks.onKeyboardFont(KeyboardFont.JETBRAINS_MONO) },
                )
            }
            if (settings.customFontStamp != 0L && customFamily != null) {
                item(key = "custom") {
                    FontCard(
                        name = settings.customFontName.ifEmpty { KeyboardFont.CUSTOM.displayName },
                        family = customFamily,
                        selected = isCustom,
                        onClick = { callbacks.onKeyboardFont(KeyboardFont.CUSTOM) },
                    )
                }
            }
            item(key = "import") {
                ImportCard(
                    busy = busyFont == IMPORTING_FONT,
                    onClick = { importFont.launch(FontMimeTypes) },
                )
            }
            if (googleAvailable) {
                header("h-google") { GroupTitle(stringResource(R.string.settings_font_google)) }
                header("search") { SearchField(query = query, onQueryChange = { query = it }) }
                header("chips") { CategoryChips(selected = category, onSelect = { category = it }) }
                items(shown, key = { "g-" + it.family }) { font ->
                    GoogleFontCard(
                        family = font.family,
                        selected = isCustom && settings.customFontName == font.family,
                        busy = busyFont == font.family,
                        onClick = { callbacks.onGoogleFont(font.family) },
                    )
                }
                if (shown.isEmpty() && catalog.isNotEmpty()) {
                    header("empty") {
                        Text(
                            text = stringResource(R.string.settings_font_none_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A full-width grid row (group titles, the search field, the chips). */
private fun LazyGridScope.header(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/**
 * The real keyboard in the current theme (and photo), drawn with the chosen font, compact:
 * letters only at a fixed row height, since this screen is about the labels.
 */
@Composable
private fun FontPreviewBand(settings: Settings, customFamily: FontFamily?) {
    val photo = rememberKeyboardPhoto(
        if (settings.keyboardTheme == KeyboardTheme.IMAGE) settings.backgroundImageStamp else 0L,
    )
    val background = photo?.let { KeyboardBackground(it, settings.backgroundDim) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        BornomalaTheme(
            theme = settings.keyboardTheme,
            font = settings.keyboardFont,
            customFont = customFamily,
            metrics = keyboardMetrics(
                horizontalGapScale = settings.horizontalGapScale,
                verticalGapScale = settings.verticalGapScale,
                keyLabelScale = settings.keyLabelScale,
                suggestionBarScale = settings.suggestionBarScale,
                bottomGapScale = settings.bottomGapScale,
                keyBorder = settings.keyBorder,
            ),
        ) {
            CompositionLocalProvider(LocalKeyboardBackground provides background) {
                KeyboardConfiguratorPreview(
                    modifier = Modifier.fillMaxWidth(),
                    showNumberRow = false,
                    rowHeight = 46.dp,
                )
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
}

/** A pill search field in the surface-container colour, as in the system settings search. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val hint = stringResource(R.string.settings_font_search)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(LucideIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = hint },
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                LucideIcons.X,
                contentDescription = stringResource(R.string.settings_font_clear_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onQueryChange("") }
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun CategoryChips(selected: GoogleFontCategory?, onSelect: (GoogleFontCategory?) -> Unit) {
    val labels = listOf(
        null to R.string.settings_font_all,
        GoogleFontCategory.SANS to R.string.settings_font_sans,
        GoogleFontCategory.SERIF to R.string.settings_font_serif,
        GoogleFontCategory.DISPLAY to R.string.settings_font_display,
        GoogleFontCategory.HANDWRITING to R.string.settings_font_handwriting,
        GoogleFontCategory.MONO to R.string.settings_font_mono,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels, key = { it.first?.key ?: "all" }) { (category, label) ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(stringResource(label)) },
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun GoogleFontCard(family: String, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    // Fetched only while the card is on screen; Play services caches each font after the first time.
    val fontFamily by produceState<FontFamily?>(initialValue = null, family) {
        value = withContext(PreviewDispatcher) { GoogleFonts.typeface(context, family)?.let { FontFamily(it) } }
    }
    FontCard(
        name = family,
        family = fontFamily,
        selected = selected,
        onClick = onClick,
        busy = busy,
        loading = fontFamily == null,
    )
}

/** A font choice: a large "Aa" drawn in the font, its name below, and the selection state. */
@Composable
private fun FontCard(
    name: String,
    family: FontFamily?,
    selected: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false,
    loading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(CardShape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, colors.primary, CardShape) else Modifier)
            .clickable(role = Role.RadioButton, enabled = !busy, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aa",
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = 40.sp,
                maxLines = 1,
                color = (if (selected) colors.onPrimaryContainer else colors.onSurface)
                    .copy(alpha = if (loading) 0.25f else 1f),
            )
            when {
                busy -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                    strokeWidth = 2.dp,
                )
                selected -> Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LucideIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The "add your own font" card, styled like a font card with a plus in place of "Aa". */
@Composable
private fun ImportCard(busy: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(CardShape)
            .border(1.dp, colors.outlineVariant, CardShape)
            .clickable(role = Role.Button, enabled = !busy, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LucideIcons.Plus, contentDescription = null, tint = colors.onSecondaryContainer)
                }
            }
        }
        Text(
            text = stringResource(R.string.settings_font_import_short),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
