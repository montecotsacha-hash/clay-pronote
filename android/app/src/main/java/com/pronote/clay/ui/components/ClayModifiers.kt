package com.pronote.clay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pronote.clay.ui.theme.ClayHighlightDark
import com.pronote.clay.ui.theme.ClayHighlightLight
import com.pronote.clay.ui.theme.ClayShadowDark
import com.pronote.clay.ui.theme.ClayShadowLight

/**
 * Claymorphism modifiers.
 *
 * A clay surface is a matte pastel block with an inflated soft shadow and a
 * subtle top-light gradient, so it reads as a plush physical object rather than
 * a flat card. Coloured ambient/spot shadows are used on API 28+; older devices
 * fall back to the standard elevation shadow automatically.
 */
@Composable
fun Modifier.clay(
    depth: Dp = 10.dp,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color? = null,
    dark: Boolean = MaterialTheme.colorScheme.background.luminanceIsDark(),
): Modifier {
    val shadow = if (dark) ClayShadowDark else ClayShadowLight
    val highlight = if (dark) ClayHighlightDark else ClayHighlightLight
    val base = tint ?: MaterialTheme.colorScheme.surface

    val top = lerpColor(base, highlight, 0.14f)
    val bottom = base

    return this
        .shadow(
            elevation = depth,
            shape = shape,
            ambientColor = shadow,
            spotColor = shadow,
        )
        .background(
            brush = Brush.linearGradient(listOf(top, bottom)),
            shape = shape,
        )
        .border(
            width = 1.dp,
            color = Color.White.copy(alpha = if (dark) 0.06f else 0.45f),
            shape = shape,
        )
}

/** Sunken clay well — for inputs, progress tracks and toggles. */
@Composable
fun Modifier.clayInset(
    shape: Shape = RoundedCornerShape(20.dp),
    dark: Boolean = MaterialTheme.colorScheme.background.luminanceIsDark(),
): Modifier {
    val base = MaterialTheme.colorScheme.background
    val inset = if (dark) lerpColor(base, Color.Black, 0.18f) else lerpColor(base, Color.Black, 0.05f)

    return this
        .background(inset, shape)
        .border(
            width = 1.dp,
            color = if (dark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.06f),
            shape = shape,
        )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

private fun Color.luminanceIsDark(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
