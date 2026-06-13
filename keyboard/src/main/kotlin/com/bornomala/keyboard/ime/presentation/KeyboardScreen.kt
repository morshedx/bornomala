package com.bornomala.keyboard.ime.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.bornomala.keyboard.theme.LucideIcons
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.KeyboardLayout
import com.bornomala.keyboard.ime.domain.model.ShiftState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.bornomala.keyboard.ime.data.layout.LayoutProvider
import com.bornomala.keyboard.ime.domain.model.Key
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyboardPage
import com.bornomala.keyboard.ime.domain.model.KeyboardPanel
import com.bornomala.keyboard.ime.domain.model.KeyboardState
import com.bornomala.keyboard.theme.BornomalaTheme

/**
 * The complete keyboard surface: suggestion bar, the active key grid, and the long-press
 * popup overlay. Rendered inside [com.bornomala.keyboard.theme.BornomalaTheme] by the IME
 * service.
 *
 * Performance:
 *  - The active [com.bornomala.keyboard.ime.domain.model.KeyboardLayout] is resolved from
 *    the [layoutProvider] in a keyed [remember] so it is a pure cache lookup that only
 *    re-runs when language/page/number-row change — never per keystroke.
 *  - [callbacks] is an `@Immutable` holder created once by the host, so key composables stay
 *    stable across recompositions (no recomposition storms on the sub-16ms key path).
 *  - The row height is derived from the user's height preference and applied per row.
 *
 * @param state immutable keyboard state to render.
 * @param layoutProvider pre-built layout cache (injected singleton).
 * @param callbacks stable input callbacks.
 * @param keyHeightFraction 0..1 position between the min and max configurable row heights.
 */
@Composable
internal fun KeyboardScreen(
    state: KeyboardState,
    layoutProvider: LayoutProvider,
    callbacks: KeyboardCallbacks,
    keyHeightFraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BornomalaTheme.keyboardColors
    val dimens = BornomalaTheme.dimens

    val layout = remember(state.language, state.page, state.showNumberRow, state.isEmailField) {
        layoutProvider.layoutFor(state.language, state.page, state.showNumberRow, state.isEmailField)
    }
    val rowHeight = remember(keyHeightFraction) {
        lerpDp(dimens.minKeyRowHeight, dimens.maxKeyRowHeight, keyHeightFraction.coerceIn(0f, 1f))
    }
    // Every page stretches its rows to the alphabetic page's row count so the IME window stays
    // the same height across pages. The numpad has 4 rows while the alpha page can have 5 (with
    // the number row on); without this its key area would be a row shorter and the window would
    // visibly jump when toggling the numpad.
    val referenceRows = remember(state.language, state.showNumberRow) {
        layoutProvider.layoutFor(state.language, KeyboardPage.ALPHA, state.showNumberRow).rows.size
    }
    val gridRowHeight = remember(rowHeight, referenceRows, layout) {
        val rows = layout.rows.size
        if (rows > 0) rowHeight * referenceRows / rows else rowHeight
    }

    // The active long-press: the key plus its on-screen bounds, so the popup can sit above it.
    var popupKey by remember { mutableStateOf<Key?>(null) }
    var popupAnchor by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Finger position (root coords) while dragging over the popup; null = use the key center.
    var popupPointer by remember { mutableStateOf<Offset?>(null) }
    // Holds the glyph currently under the finger so release can commit it without recomputing.
    val popupSelection = remember { intArrayOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rootCoordinates = it }
            .background(colors.keyboardBackground)
            // Reserve the gesture/navigation-bar inset: the tray background paints to the
            // bottom edge while the keys sit above the gesture pill (no black gap, no overlap).
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dimens.keyboardHorizontalPadding,
                    end = dimens.keyboardHorizontalPadding,
                    // No top padding: the toolbar/suggestion strip sits flush with the tray
                    // top (Gboard-style). Its own internal padding gives the icons breathing
                    // room, so there is no bare keyboard-background band above the strip that
                    // would make the toolbar look top-heavy.
                    top = 0.dp,
                    // Extra bottom margin (above the gesture inset) so the last row clears the
                    // gesture handle comfortably. User-tunable via the configurator.
                    bottom = dimens.keyboardVerticalPadding + BornomalaTheme.metrics.bottomGap,
                ),
        ) {
            // The emoji panel hides the tools/suggestion strip (Gboard-style): its own top bar
            // carries a back arrow + category tabs, so the strip would be redundant.
            if (state.panel != KeyboardPanel.EMOJI) {
                ActionStrip(
                    suggestions = if (state.suggestionsEnabled) state.suggestions else emptyList(),
                    hasText = state.hasText,
                    emojiActive = state.panel == KeyboardPanel.EMOJI,
                    clipboardActive = state.panel == KeyboardPanel.CLIPBOARD,
                    numpadActive = state.page == KeyboardPage.NUMPAD,
                    settingsActive = state.panel == KeyboardPanel.SETTINGS,
                    callbacks = callbacks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp * BornomalaTheme.metrics.suggestionBarScale),
                )
            }

            // Panels are sized to the alphabetic keyboard's key area so the IME window stays
            // keyboard-height instead of expanding to fill the screen.
            val panelHeight = remember(state.language, state.showNumberRow, rowHeight) {
                val rows = layoutProvider
                    .layoutFor(state.language, KeyboardPage.ALPHA, state.showNumberRow)
                    .rows.size
                rowHeight * rows
            }

            if (state.panel == KeyboardPanel.SETTINGS) {
                // In-keyboard settings menu: a category grid that opens the full app per section.
                // No search; sized to the alpha key area so the window stays keyboard-height.
                SettingsMenuPanel(
                    onOpenSection = callbacks.onOpenSettingsSection,
                    modifier = Modifier.fillMaxWidth().height(panelHeight),
                )
            } else if (state.panel != KeyboardPanel.NONE) {
                // Search bar; tapping it reveals the keyboard below (Gboard-style) whose
                // keystrokes feed the panel query rather than the text field. For emoji it sits
                // inside the panel (below the back+tabs bar); for clipboard it sits above the host.
                val searchBar: @Composable () -> Unit = {
                    PanelSearchBar(
                        query = state.panelQuery,
                        active = state.panelSearchActive,
                        isEmoji = state.panel == KeyboardPanel.EMOJI,
                        onActivate = callbacks.onOpenSearch,
                        onClose = callbacks.onCloseSearch,
                    )
                }
                // The emoji panel suppresses the tools/suggestion strip, so its host reclaims
                // that strip's height — keeping the whole IME window the exact same height as the
                // keyboard (strip + key area) instead of shrinking when emoji opens.
                val stripHeight = 48.dp * BornomalaTheme.metrics.suggestionBarScale
                val baseHeight = if (state.panel == KeyboardPanel.EMOJI) {
                    panelHeight + stripHeight
                } else {
                    panelHeight
                }
                val hostHeight = if (state.panelSearchActive) baseHeight * 0.5f else baseHeight
                val hostModifier = Modifier.fillMaxWidth().height(hostHeight)
                if (state.panel == KeyboardPanel.EMOJI) {
                    EmojiHost(
                        onEmoji = callbacks.onEmoji,
                        query = state.panelQuery,
                        onBack = callbacks.onToggleEmoji,
                        searchBar = searchBar,
                        modifier = hostModifier,
                    )
                } else {
                    // Clipboard: the search bar sits INSIDE the fixed key-area height (not added
                    // on top of it), so switching keyboard<->clipboard keeps the window height
                    // identical to the keyboard's (strip + key area).
                    Column(modifier = hostModifier) {
                        searchBar()
                        ClipboardHost(
                            onPaste = callbacks.onPaste,
                            query = state.panelQuery,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
                if (state.panelSearchActive) {
                    val alphaLayout = remember(state.language) {
                        layoutProvider.layoutFor(state.language, KeyboardPage.ALPHA, showNumberRow = false)
                    }
                    KeyGrid(
                        layout = alphaLayout,
                        shift = state.shift,
                        enterIsAccent = false,
                        rowHeight = rowHeight,
                        onKey = callbacks.onSearchKey,
                        onLongPressChar = {},
                        onLongPressRequested = { _, _ -> },
                        onLongPressMove = {},
                        onLongPressReleased = {},
                        activePopupKey = null,
                    )
                }
            } else {
                KeyGrid(
                    layout = layout,
                    shift = state.shift,
                    enterIsAccent = state.enterIsAccent,
                    rowHeight = gridRowHeight,
                    onKey = callbacks.onKey,
                    onLongPressChar = callbacks.onLongPressChar,
                    onLongPressRequested = { pressedKey, coords ->
                        // Long-pressing the comma opens keyboard settings (Gboard-style).
                        if (pressedKey.action == KeyAction.Character(',')) {
                            callbacks.onOpenSettings()
                        } else {
                            popupKey = pressedKey
                            popupAnchor = coords
                            popupPointer = null
                            popupSelection[0] = 0
                        }
                    },
                    onLongPressMove = { windowPos ->
                        popupPointer = rootCoordinates?.windowToLocal(windowPos)
                    },
                    onLongPressReleased = {
                        val k = popupKey
                        if (k != null) {
                            k.longPressChars.getOrNull(popupSelection[0])
                                ?.let { callbacks.onLongPressChar(it) }
                        }
                        popupKey = null
                        popupPointer = null
                    },
                    activePopupKey = popupKey,
                )
            }
        }

        val activePopup = popupKey
        val anchorCoords = popupAnchor
        val root = rootCoordinates
        if (activePopup != null && anchorCoords != null && root != null && anchorCoords.isAttached) {
            // Key bounds in the keyboard's own coordinate space, so the popup can be drawn
            // directly above the pressed key.
            val anchor = root.localBoundingBoxOf(anchorCoords, clipBounds = false)
            LongPressPopup(
                chars = popupChars(activePopup),
                anchor = anchor,
                keyboardWidth = root.size.width.toFloat(),
                pointer = popupPointer,
                onSelectedIndex = { popupSelection[0] = it },
                // Match the keyboard's own bounds (not the whole screen) so the dismiss scrim
                // never forces the IME window to grow to full height — which would shove the
                // keyboard to the top with a large empty band below it.
                modifier = Modifier.matchParentSize(),
                onPick = {
                    callbacks.onLongPressChar(it)
                    popupKey = null
                    popupPointer = null
                },
                onDismiss = {
                    popupKey = null
                    popupPointer = null
                },
            )
        }
    }
}

/**
 * A live, non-interactive replica of the real keyboard for the settings configurator sheet.
 *
 * Renders the same [KeyGrid]/[KeyView] composables the IME uses, so it reflects the active
 * [BornomalaTheme] colors, font and [com.bornomala.keyboard.theme.KeyboardMetrics] (gaps,
 * label size, border) exactly. Dragging a configurator slider updates this preview because
 * the host wraps it in a [BornomalaTheme] whose metrics come from the live settings — there
 * is no separate mock to keep in sync.
 *
 * Laid out at a fixed footprint (constant [rowHeight], no navigation insets) so the enclosing
 * bottom sheet never changes height while a slider is dragged. A transparent overlay swallows
 * touches so the preview is display-only.
 */
/** No-op callbacks for the display-only configurator preview. */
private val PreviewCallbacks = KeyboardCallbacks(
    onKey = {},
    onLongPressChar = {},
    onSuggestion = {},
    onOpenSettings = {},
    onToggleSettingsMenu = {},
    onOpenSettingsSection = {},
    onToggleEmoji = {},
    onToggleNumbers = {},
    onToggleClipboard = {},
    onPaste = {},
    onEmoji = {},
    onHideKeyboard = {},
    onSearchKey = {},
    onOpenSearch = {},
    onCloseSearch = {},
)

@Composable
fun KeyboardConfiguratorPreview(modifier: Modifier = Modifier) {
    val colors = BornomalaTheme.keyboardColors
    val metrics = BornomalaTheme.metrics
    val layout = remember {
        LayoutProvider().layoutFor(
            language = KeyboardLanguage.ENGLISH,
            page = KeyboardPage.ALPHA,
            showNumberRow = false,
        )
    }
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.keyboardBackground)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            // The real toolbar/suggestion bar. With no suggestions it shows the tools row,
            // exactly like the live keyboard before the user starts typing.
            ActionStrip(
                suggestions = emptyList(),
                hasText = false,
                emojiActive = false,
                clipboardActive = false,
                numpadActive = false,
                settingsActive = false,
                callbacks = PreviewCallbacks,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp * metrics.suggestionBarScale),
            )
            KeyGrid(
                layout = layout,
                shift = ShiftState.OFF,
                enterIsAccent = true,
                rowHeight = 56.dp,
                onKey = {},
                onLongPressChar = {},
                onLongPressRequested = { _, _ -> },
                onLongPressMove = {},
                onLongPressReleased = {},
                activePopupKey = null,
            )
        }
        // Display-only: consume all pointer events so taps never alter the preview.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        )
    }
}

/** Renders a layout's key rows. Reused by the normal keyboard and the in-panel search keyboard. */
@Composable
private fun KeyGrid(
    layout: KeyboardLayout,
    shift: ShiftState,
    enterIsAccent: Boolean,
    rowHeight: Dp,
    onKey: (KeyAction) -> Unit,
    onLongPressChar: (Char) -> Unit,
    onLongPressRequested: (Key, LayoutCoordinates) -> Unit,
    onLongPressMove: (Offset) -> Unit,
    onLongPressReleased: () -> Unit,
    activePopupKey: Key?,
) {
    val strip = layout.scrollableLeftStrip
    if (strip != null) {
        // Gboard numeric pad: a scrollable symbol strip pinned left of the digit grid, with the
        // layout's last row laid out full-width beneath both. The strip spans the digit rows.
        val gridRows = layout.rows.dropLast(1)
        val bottomRow = layout.rows.lastOrNull()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * gridRows.size),
        ) {
            // Left strip: a scrollable rail = 1 column, identical in size to a right-rail key.
            // 7-column model: strip weight 1 : grid weight 6; digit rows are
            // [digit 5/3, digit 5/3, digit 5/3, rail 1] = 6, so strip(1) and right rail(1) match
            // and the 3 digits share the middle 5 columns equally.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                strip.forEach { key ->
                    KeyView(
                        key = key,
                        shift = shift,
                        enterIsAccent = enterIsAccent,
                        onKey = onKey,
                        onLongPressChar = onLongPressChar,
                        onLongPressRequested = onLongPressRequested,
                        onLongPressMove = onLongPressMove,
                        onLongPressReleased = onLongPressReleased,
                        isPopupSource = key === activePopupKey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                    )
                }
            }
            // Digit grid + right column. Weight 6 = the 3 digits (5/3 each = 5) + right rail (1).
            Column(modifier = Modifier.weight(6f).fillMaxHeight()) {
                gridRows.forEach { row ->
                    KeyRowView(
                        row = row,
                        shift = shift,
                        enterIsAccent = enterIsAccent,
                        rowHeight = rowHeight,
                        onKey = onKey,
                        onLongPressChar = onLongPressChar,
                        onLongPressRequested = onLongPressRequested,
                        onLongPressMove = onLongPressMove,
                        onLongPressReleased = onLongPressReleased,
                        activePopupKey = activePopupKey,
                    )
                }
            }
        }
        if (bottomRow != null) {
            KeyRowView(
                row = bottomRow,
                shift = shift,
                enterIsAccent = enterIsAccent,
                rowHeight = rowHeight,
                onKey = onKey,
                onLongPressChar = onLongPressChar,
                onLongPressRequested = onLongPressRequested,
                onLongPressMove = onLongPressMove,
                onLongPressReleased = onLongPressReleased,
                activePopupKey = activePopupKey,
            )
        }
        return
    }

    layout.rows.forEach { row ->
        KeyRowView(
            row = row,
            shift = shift,
            enterIsAccent = enterIsAccent,
            rowHeight = rowHeight,
            onKey = onKey,
            onLongPressChar = onLongPressChar,
            onLongPressRequested = onLongPressRequested,
            onLongPressMove = onLongPressMove,
            onLongPressReleased = onLongPressReleased,
            activePopupKey = activePopupKey,
        )
    }
}

/** One full-width weighted row of keys; the building block of every layout's grid. */
@Composable
private fun KeyRowView(
    row: com.bornomala.keyboard.ime.domain.model.KeyRow,
    shift: ShiftState,
    enterIsAccent: Boolean,
    rowHeight: Dp,
    onKey: (KeyAction) -> Unit,
    onLongPressChar: (Char) -> Unit,
    onLongPressRequested: (Key, LayoutCoordinates) -> Unit,
    onLongPressMove: (Offset) -> Unit,
    onLongPressReleased: () -> Unit,
    activePopupKey: Key?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight),
    ) {
        // Half-key gutter (Gboard/Samsung home-row indent). Empty so the keys stay aligned
        // under the row above without stretching edge-to-edge.
        if (row.edgeWeight > 0f) Spacer(Modifier.weight(row.edgeWeight))
        row.keys.forEach { key ->
            KeyView(
                key = key,
                shift = shift,
                enterIsAccent = enterIsAccent,
                onKey = onKey,
                onLongPressChar = onLongPressChar,
                onLongPressRequested = onLongPressRequested,
                onLongPressMove = onLongPressMove,
                onLongPressReleased = onLongPressReleased,
                isPopupSource = key === activePopupKey,
                modifier = Modifier
                    .weight(key.weight)
                    .fillMaxHeight(),
            )
        }
        if (row.edgeWeight > 0f) Spacer(Modifier.weight(row.edgeWeight))
    }
}

/** The search field shown atop the emoji/clipboard panel; tapping it reveals the keyboard. */
@Composable
private fun PanelSearchBar(
    query: String,
    active: Boolean,
    isEmoji: Boolean,
    onActivate: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = BornomalaTheme.keyboardColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.keyBackground)
            .clickable { if (!active) onActivate() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LucideIcons.Search,
            contentDescription = null,
            tint = colors.suggestionText.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = query.ifEmpty { if (isEmoji) "Search emoji" else "Search clipboard" },
            color = if (query.isEmpty()) colors.suggestionText.copy(alpha = 0.6f) else colors.keyContent,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            Icon(
                imageVector = LucideIcons.X,
                contentDescription = "Close search",
                tint = colors.suggestionText.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp).clickable { onClose() },
            )
        }
    }
}

/**
 * Long-press alternates popup, drawn as a rounded card directly above the pressed key
 * ([anchor] = the key's bounds in this overlay's coordinate space). Like Gboard / HeliBoard's
 * "more keys": many alternates wrap into a grid (balanced rows, capped width), index 0 sits
 * bottom-left, and a dim scrim covers the keyboard behind it.
 *
 * Selection follows the finger: [pointer] is the current touch in overlay coordinates (null
 * until the user moves), and the cell under it is highlighted with the theme accent. The
 * resolved index is reported via [onSelectedIndex] so release can commit it; tapping a cell
 * also commits via [onPick].
 */
@Composable
private fun LongPressPopup(
    chars: List<Char>,
    anchor: androidx.compose.ui.geometry.Rect,
    keyboardWidth: Float,
    pointer: Offset?,
    onSelectedIndex: (Int) -> Unit,
    onPick: (Char) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BornomalaTheme.keyboardColors
    if (chars.isEmpty()) {
        onDismiss()
        return
    }
    val n = chars.size
    val maxPerRow = 5
    val rows = (n + maxPerRow - 1) / maxPerRow
    val perRow = (n + rows - 1) / rows

    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemDp = 46.dp
    val padDp = 6.dp
    val gapDp = 6.dp
    val itemPx = with(density) { itemDp.toPx() }
    val padPx = with(density) { padDp.toPx() }
    val gapPx = with(density) { gapDp.toPx() }

    val gridW = perRow * itemPx + 2 * padPx
    val gridH = rows * itemPx + 2 * padPx
    val left = (anchor.center.x - gridW / 2f).coerceIn(0f, (keyboardWidth - gridW).coerceAtLeast(0f))
    val top = (anchor.top - gridH - gapPx).coerceAtLeast(0f)
    val contentLeft = left + padPx
    val contentTop = top + padPx

    // Index under the finger (or the key center before the first move). Index 0 is bottom-left.
    val px = pointer?.x ?: anchor.center.x
    val py = pointer?.y ?: (contentTop + (rows - 0.5f) * itemPx)
    val col = ((px - contentLeft) / itemPx).toInt().coerceIn(0, perRow - 1)
    val rowFromTop = ((py - contentTop) / itemPx).toInt().coerceIn(0, rows - 1)
    val rowFromBottom = rows - 1 - rowFromTop
    val selected = (rowFromBottom * perRow + col).coerceIn(0, n - 1)
    androidx.compose.runtime.SideEffect { onSelectedIndex(selected) }

    Box(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(left.roundToInt(), top.roundToInt()) }
                .clip(RoundedCornerShape(12.dp))
                .background(colors.popupBackground)
                .border(1.dp, colors.keyStroke, RoundedCornerShape(12.dp))
                .padding(padDp),
        ) {
            // Render top→bottom; index 0 lives in the bottom row, increasing left→right upward.
            for (visualRow in 0 until rows) {
                val rowBottom = rows - 1 - visualRow
                val start = rowBottom * perRow
                val end = minOf(start + perRow, n)
                Row {
                    for (i in start until end) {
                        val isSel = i == selected
                        Box(
                            modifier = Modifier
                                .size(itemDp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) colors.accentKeyBackground else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { onPick(chars[i]) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = chars[i].toString(),
                                color = if (isSel) colors.accentKeyContent else colors.popupContent,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun popupChars(key: Key): List<Char> {
    // Only the alternates — never the key's own base character (the user already sees that on
    // the key itself; repeating it in the popup is confusing).
    return key.longPressChars
}

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    start + (stop - start) * fraction
