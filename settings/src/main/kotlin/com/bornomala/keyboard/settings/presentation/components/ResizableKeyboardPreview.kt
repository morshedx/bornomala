package com.bornomala.keyboard.settings.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bornomala.keyboard.ime.presentation.KeyboardConfiguratorPreview
import com.bornomala.keyboard.settings.R
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.theme.BornomalaTheme
import com.bornomala.keyboard.theme.KeyboardBackground
import com.bornomala.keyboard.theme.KeyboardBackgroundImage
import com.bornomala.keyboard.theme.KeyboardDimens
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardFontFile
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.keyboardTray
import com.bornomala.keyboard.theme.keyboardColorsFor
import com.bornomala.keyboard.theme.keyboardMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Where the height handle sits, which also decides which drag direction makes keys taller. */
enum class HeightHandle {
    /** On the top edge: drag up for taller keys (a keyboard anchored to the screen bottom). */
    TOP,

    /** On the bottom edge: drag down for taller keys (a keyboard hanging from above). */
    BOTTOM,
}

/**
 * The real keyboard rendered from [settings] (theme, font, gaps, label and bar sizes, number
 * row, height), with a drag handle on one edge to change the keyboard height directly. The
 * preview follows the finger; [onHeightChange] is called once, with the height snapped to 5%
 * steps, when the drag ends. TalkBack users get it as an adjustable slider.
 */
@Composable
fun ResizableKeyboardPreview(
    settings: Settings,
    onHeightChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    handle: HeightHandle = HeightHandle.TOP,
    shape: Shape = RoundedCornerShape(24.dp),
) {
    val colors = keyboardColorsFor(settings.keyboardTheme, isSystemInDarkTheme())
    val photo = rememberKeyboardPhoto(
        if (settings.keyboardTheme == KeyboardTheme.IMAGE) settings.backgroundImageStamp else 0L,
    )
    val background = photo?.let { KeyboardBackground(it, settings.backgroundDim) }
    val customFont = rememberKeyboardFont(
        if (settings.keyboardFont == KeyboardFont.CUSTOM) settings.customFontStamp else 0L,
    )
    var dragging by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(settings.keyboardHeightScale) }
    LaunchedEffect(settings.keyboardHeightScale) {
        if (!dragging) scale = settings.keyboardHeightScale
    }
    val snapped = snapKeyboardHeight(scale)
    val percent = (snapped * 100f).roundToInt()
    val rows = if (settings.numberRowEnabled) 5 else 4
    val rowPx = with(LocalDensity.current) { KeyboardDimens.keyRowHeight.toPx() }
    val rowHeight = (KeyboardDimens.keyRowHeight * snapped)
        .coerceIn(KeyboardDimens.minKeyRowHeight, KeyboardDimens.maxKeyRowHeight)
    val growSign = if (handle == HeightHandle.TOP) -1f else 1f
    val dragState = rememberDraggableState { delta ->
        scale = (scale + growSign * delta / (rows * rowPx))
            .coerceIn(Settings.MIN_KEYBOARD_HEIGHT_SCALE, Settings.MAX_KEYBOARD_HEIGHT_SCALE)
    }
    val handleWidth by animateDpAsState(if (dragging) 52.dp else 40.dp, label = "handle-width")
    val heightLabel = stringResource(R.string.settings_keyboard_height)
    val percentText = stringResource(R.string.settings_keyboard_height_value, percent)

    val handleStrip: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { dragging = true },
                    onDragStopped = {
                        dragging = false
                        onHeightChange(snapKeyboardHeight(scale))
                    },
                )
                .semantics {
                    contentDescription = heightLabel
                    stateDescription = percentText
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = snapped,
                        range = Settings.MIN_KEYBOARD_HEIGHT_SCALE..Settings.MAX_KEYBOARD_HEIGHT_SCALE,
                        steps = 12,
                    )
                    setProgress { target ->
                        val value = snapKeyboardHeight(target)
                        scale = value
                        onHeightChange(value)
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(handleWidth)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(colors.keyContent.copy(alpha = if (dragging) 0.9f else 0.5f)),
            )
            Text(
                text = percentText,
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentKeyContent,
                modifier = Modifier
                    .offset(x = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accentKeyBackground)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }

    Column(
        modifier
            .clip(shape)
            // One tray (and photo) behind both the handle strip and the keys.
            .keyboardTray(colors.keyboardBackground, background),
    ) {
        if (handle == HeightHandle.TOP) handleStrip()
        BornomalaTheme(
            theme = settings.keyboardTheme,
            font = settings.keyboardFont,
            customFont = customFont,
            metrics = keyboardMetrics(
                horizontalGapScale = settings.horizontalGapScale,
                verticalGapScale = settings.verticalGapScale,
                keyLabelScale = settings.keyLabelScale,
                suggestionBarScale = settings.suggestionBarScale,
                bottomGapScale = settings.bottomGapScale,
                keyBorder = settings.keyBorder,
            ),
        ) {
            KeyboardConfiguratorPreview(
                modifier = Modifier.fillMaxWidth(),
                showNumberRow = settings.numberRowEnabled,
                rowHeight = rowHeight,
                bangla = true,
                drawTray = false,
            )
        }
        if (handle == HeightHandle.BOTTOM) handleStrip()
    }
}

/**
 * The keyboard photo for [stamp] (0 = none), decoded off the main thread; null until loaded.
 * A new stamp (a newly picked photo) reloads it.
 */
@Composable
fun rememberKeyboardPhoto(stamp: Long): ImageBitmap? {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, stamp) {
        value = if (stamp == 0L) null else withContext(Dispatchers.IO) { KeyboardBackgroundImage.load(context) }
    }
    return photo
}

/**
 * The saved custom keyboard font for [stamp] (0 = none), loaded off the main thread; null until
 * loaded. A new stamp (a newly saved font) reloads it.
 */
@Composable
fun rememberKeyboardFont(stamp: Long): FontFamily? {
    val context = LocalContext.current
    val font by produceState<FontFamily?>(initialValue = null, stamp) {
        value = if (stamp == 0L) null else withContext(Dispatchers.IO) { KeyboardFontFile.load(context) }
    }
    return font
}

/** Keyboard height in 5% steps within the allowed range, matching the settings slider. */
fun snapKeyboardHeight(scale: Float): Float =
    ((scale * 20f).roundToInt() / 20f)
        .coerceIn(Settings.MIN_KEYBOARD_HEIGHT_SCALE, Settings.MAX_KEYBOARD_HEIGHT_SCALE)
