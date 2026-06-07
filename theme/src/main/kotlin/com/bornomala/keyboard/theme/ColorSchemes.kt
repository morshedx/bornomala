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
    // Dark scheme aligned with the companion "pennyo" app (sage/olive Material You).
    primary = androidx.compose.ui.graphics.Color(0xFF7E9F70),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF0F1409),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF2C3D24),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFC9E4BD),
    secondary = androidx.compose.ui.graphics.Color(0xFFBDCDB1),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF1F2A1A),
    background = androidx.compose.ui.graphics.Color(0xFF1C1B1F),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE6E6E2),
    surface = androidx.compose.ui.graphics.Color(0xFF211F26),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE6E6E2),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2B2930),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFA9ABA4),
    outline = androidx.compose.ui.graphics.Color(0xFF3A3A3A),
    error = BornomalaPalette.ErrorDark,
    onError = BornomalaPalette.Grey10,
)
