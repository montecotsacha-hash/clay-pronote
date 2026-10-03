package com.pronote.clay.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * AppTheme — the single centralised theme.
 *
 * Supports light, dark and Material You dynamic colour, plus an opt-out that
 * keeps the pastel clay palette even when dynamic colour is available.
 */
private val LightColors = lightColorScheme(
    primary = ClayPrimaryLight,
    onPrimary = ClayOnPrimaryLight,
    secondary = ClaySecondaryLight,
    onSecondary = ClayOnSurfaceLight,
    tertiary = ClayAccentLight,
    onTertiary = ClayOnSurfaceLight,
    background = ClayBackgroundLight,
    onBackground = ClayOnSurfaceLight,
    surface = ClaySurfaceLight,
    onSurface = ClayOnSurfaceLight,
    surfaceVariant = ClaySecondaryLight,
    onSurfaceVariant = ClayMutedLight,
    outline = ClayMutedLight,
    error = ClayErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = ClayPrimaryDark,
    onPrimary = ClayOnPrimaryDark,
    secondary = ClaySecondaryDark,
    onSecondary = ClayOnSurfaceDark,
    tertiary = ClayAccentDark,
    onTertiary = ClayOnSurfaceDark,
    background = ClayBackgroundDark,
    onBackground = ClayOnSurfaceDark,
    surface = ClaySurfaceDark,
    onSurface = ClayOnSurfaceDark,
    surfaceVariant = ClaySecondaryDark,
    onSurfaceVariant = ClayMutedDark,
    outline = ClayMutedDark,
    error = ClayErrorDark,
)

@Composable
fun AppTheme(
    /** null follows the system setting. */
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = darkTheme ?: isSystemInDarkTheme()

    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ClayTypography,
        shapes = ClayShapes,
        content = content,
    )
}

/** Shared clay dimensions. */
object ClayDimens {
    val ScreenPadding = 20.dp
    val CardPadding = 20.dp
    val SectionGap = 24.dp
    val ItemGap = 12.dp
    val MinTouchTarget = 48.dp
}
