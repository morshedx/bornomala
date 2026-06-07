package com.bornomala.keyboard.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * Material 3 shapes for app surfaces. Samsung One UI uses generously rounded
 * corners; these err toward larger radii than the Material defaults.
 */
internal val BornomalaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Keyboard-specific corner radii. Keys in One UI are softly rounded rectangles;
 * popups and the emoji/clipboard panels reuse the larger app radii.
 */
@Immutable
object KeyboardShapeTokens {
    val keyCornerRadius = 8.dp
    val functionalKeyCornerRadius = 8.dp
    val spacebarCornerRadius = 10.dp
    val popupCornerRadius = 12.dp
    val suggestionChipCornerRadius = 18.dp
}
