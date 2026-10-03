package com.pronote.clay.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pronote.clay.ui.theme.ClayDimens

/**
 * ClaySurface — the base plush panel used across the whole app.
 */
@Composable
fun ClaySurface(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    depth: androidx.compose.ui.unit.Dp = 10.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(28.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A gentle squish on press keeps the clay feeling tactile without being showy.
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "clay-press")

    Box(
        modifier = modifier
            .scale(scale)
            .clay(depth = depth, shape = shape, tint = tint)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else Modifier,
            ),
    ) {
        content()
    }
}

/** Small pastel clay pill — badges, source labels, counts. */
@Composable
fun ClayPill(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tint.copy(alpha = 0.24f),
        contentColor = tint,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Section heading with a small clay dot accent. */
@Composable
fun ClaySectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clay(depth = 3.dp, shape = CircleShape, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            )
            Text(text, style = MaterialTheme.typography.headlineMedium)
        }
        action?.invoke()
    }
}

/** Clay button with a pressed squish and a loading state. */
@Composable
fun ClayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "button-press")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ClayDimens.MinTouchTarget)
            .scale(scale)
            .clay(depth = 10.dp, shape = RoundedCornerShape(22.dp), tint = tint)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            ),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = contentColor)
            }
            if (loading || icon != null) {
                Box(Modifier.size(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
            )
        }
    }
}

/** Secondary clay button: lighter, still tactile. */
@Composable
fun ClaySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    ClayButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        loading = loading,
        tint = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
    )
}

/** Rounded clay icon button. */
@Composable
fun ClayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(ClayDimens.MinTouchTarget)
            .clay(depth = 8.dp, shape = RoundedCornerShape(18.dp)),
        color = Color.Transparent,
        onClick = onClick,
    ) {
        Box(Modifier.size(ClayDimens.MinTouchTarget), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

/** Row used by the settings screens: label, description and a trailing control. */
@Composable
fun ClaySettingRow(
    title: String,
    description: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** Divider that reads as a soft clay seam rather than a hard line. */
@Composable
fun ClayDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .size(1.dp)
            .clayInset(shape = CircleShape),
    )
}

/** Loading state placeholder that keeps the layout stable. */
@Composable
fun ClayLoading(label: String = "Chargement…") {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Box(Modifier.size(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Empty state with a soft clay frame. */
@Composable
fun ClayEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    ClaySurface(modifier = modifier.fillMaxWidth(), depth = 6.dp) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Box(Modifier.size(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Soft clay outline used for inline error messages. */
@Composable
fun ClayErrorBox(message: String, hint: String? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            if (hint != null) {
                Box(Modifier.size(4.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }
        }
    }
}
