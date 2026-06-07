package com.bornomala.keyboard.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Material 3 color schemes derived from [BornomalaPalette]. Used by app-level
 * Compose surfaces (settings, dialogs). The keyboard itself additionally consumes
 * [KeyboardColors] for its bespoke key surfaces.
 */
internal val BornomalaLightColorScheme = lightColorScheme(
    primary = BornomalaPalette.Blue40,
    onPrimary = BornomalaPalette.White,
    primaryContainer = BornomalaPalette.Blue90,
    onPrimaryContainer = BornomalaPalette.Blue40,
    secondary = BornomalaPalette.OnLightVariant,
    onSecondary = BornomalaPalette.White,
    background = BornomalaPalette.Grey99,
    onBackground = BornomalaPalette.OnLight,
    surface = BornomalaPalette.White,
    onSurface = BornomalaPalette.OnLight,
    surfaceVariant = BornomalaPalette.Grey95,
    onSurfaceVariant = BornomalaPalette.OnLightVariant,
    outline = BornomalaPalette.Grey85,
    error = BornomalaPalette.Error,
    onError = BornomalaPalette.White,
)

internal val BornomalaDarkColorScheme = darkColorScheme(
    primary = BornomalaPalette.Blue50,
    onPrimary = BornomalaPalette.White,
    primaryContainer = BornomalaPalette.Blue40,
    onPrimaryContainer = BornomalaPalette.Blue90,
    secondary = BornomalaPalette.OnDarkVariant,
    onSecondary = BornomalaPalette.Grey10,
    background = androidx.compose.ui.graphics.Color(0xFF1A2517),
    onBackground = BornomalaPalette.OnDark,
    surface = androidx.compose.ui.graphics.Color(0xFF1F2B1C),
    onSurface = BornomalaPalette.OnDark,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF273325),
    onSurfaceVariant = BornomalaPalette.OnDarkVariant,
    outline = androidx.compose.ui.graphics.Color(0xFF3A4A36),
    error = BornomalaPalette.ErrorDark,
    onError = BornomalaPalette.Grey10,
)
