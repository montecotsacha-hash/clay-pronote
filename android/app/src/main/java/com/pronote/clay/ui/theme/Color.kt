package com.pronote.clay.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Claymorphism palette — matte pastels, plush surfaces.
 * Kept in sync with the product's web palette so both feel like one product.
 */

// Light
val ClayBackgroundLight = Color(0xFFF3EEFB)
val ClaySurfaceLight = Color(0xFFFAF7FE)
val ClayOnSurfaceLight = Color(0xFF3A2E55)
val ClayPrimaryLight = Color(0xFF9B7BE0)
val ClayOnPrimaryLight = Color(0xFFFDFBFF)
val ClaySecondaryLight = Color(0xFFE6DDF5)
val ClayAccentLight = Color(0xFFA9E5DA)
val ClayMutedLight = Color(0xFF6B5F87)
val ClayErrorLight = Color(0xFFC9645C)

// Dark
val ClayBackgroundDark = Color(0xFF241F33)
val ClaySurfaceDark = Color(0xFF2E2842)
val ClayOnSurfaceDark = Color(0xFFF0ECF9)
val ClayPrimaryDark = Color(0xFFBBA3F0)
val ClayOnPrimaryDark = Color(0xFF2A2240)
val ClaySecondaryDark = Color(0xFF3C3555)
val ClayAccentDark = Color(0xFF4E8C84)
val ClayMutedDark = Color(0xFFB0A6C6)
val ClayErrorDark = Color(0xFFE09A94)

/** Shadow tints that give clay its inflated depth. */
val ClayShadowLight = Color(0x331F1440)
val ClayHighlightLight = Color(0xE6FFFFFF)
val ClayShadowDark = Color(0x8A0B0716)
val ClayHighlightDark = Color(0x40FFFFFF)

/** Per-subject accents for the clay charts and pills. */
val SubjectAccents = listOf(
    Color(0xFF9B7BE0),
    Color(0xFF6FC7B8),
    Color(0xFFE8C36B),
    Color(0xFFE59AC4),
    Color(0xFFE79A6B),
    Color(0xFF7FB3E8),
)

fun subjectAccent(index: Int): Color = SubjectAccents[index.mod(SubjectAccents.size)]
