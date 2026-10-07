package com.pronote.clay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pronote.clay.ui.components.ClayButton
import com.pronote.clay.ui.components.ClaySurface
import com.pronote.clay.ui.components.clay

/**
 * LockScreen — shown INSTEAD of the app while the optional biometric lock is
 * on. Nothing behind it is composed, so no school data can be read from a
 * screenshot, a recents preview or over the shoulder.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        ClaySurface(
            modifier = Modifier.padding(horizontal = 26.dp),
            depth = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clay(
                            depth = 8.dp,
                            shape = RoundedCornerShape(22.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Ardoise est verrouillé",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Empreinte, visage ou code de l'appareil requis pour reprendre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                ClayButton(
                    text = "Déverrouiller",
                    onClick = onUnlock,
                    icon = Icons.Rounded.Fingerprint,
                    modifier = Modifier.width(210.dp),
                )
            }
        }
    }
}
