package com.pronote.clay.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pronote.clay.domain.Preferences
import com.pronote.clay.ui.components.ClayButton
import com.pronote.clay.ui.components.ClayDivider
import com.pronote.clay.ui.components.ClaySecondaryButton
import com.pronote.clay.ui.components.ClaySettingRow
import com.pronote.clay.ui.viewmodel.HomeViewModel

/**
 * SettingsSheet — preferences, account actions and the privacy reminder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(viewModel: HomeViewModel, onDismiss: () -> Unit) {
    val preferences by viewModel.preferences.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 34.dp),
        ) {
            Text("Réglages", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(18.dp))

            Section("Synchronisation")
            ToggleRow(
                title = "Synchronisation automatique",
                description = "Synchronise périodiquement tes notes quand l'appareil est connecté à Internet.",
                checked = preferences.autoSync,
                onChange = { viewModel.updatePreferences(preferences.copy(autoSync = it)) },
            )

            Section("Notifications")
            ToggleRow(
                title = "Nouvelles notes",
                description = "Une alerte locale après une synchronisation en arrière-plan.",
                checked = preferences.notifyNewGrades,
                onChange = { viewModel.updatePreferences(preferences.copy(notifyNewGrades = it)) },
            )
            ToggleRow(
                title = "Changement de moyenne",
                description = "Préviens-moi quand la moyenne générale bouge d'au moins 0,05.",
                checked = preferences.notifyAverageChange,
                onChange = { viewModel.updatePreferences(preferences.copy(notifyAverageChange = it)) },
            )

            Section("Apparence")
            ClaySettingRow(
                title = "Thème",
                description = "Sombre, clair ou celui du système.",
            ) {
                Row {
                    ThemeChip("Système", preferences.darkTheme == null) {
                        viewModel.updatePreferences(preferences.copy(darkTheme = null))
                    }
                    ThemeChip("Clair", preferences.darkTheme == false) {
                        viewModel.updatePreferences(preferences.copy(darkTheme = false))
                    }
                    ThemeChip("Sombre", preferences.darkTheme == true) {
                        viewModel.updatePreferences(preferences.copy(darkTheme = true))
                    }
                }
            }
            ToggleRow(
                title = "Couleurs Material You",
                description = "Reprends les couleurs de ton fond d'écran (Android 12+).",
                checked = preferences.dynamicColor,
                onChange = { viewModel.updatePreferences(preferences.copy(dynamicColor = it)) },
            )
            Spacer(Modifier.height(10.dp))
            ClaySettingRow(
                title = "Prénom à afficher",
                description = "Utilisé après « Bonjour » si Pronote ne fournit pas de prénom.",
            ) {}
            OutlinedTextField(
                value = preferences.displayName.orEmpty(),
                onValueChange = {
                    viewModel.updatePreferences(preferences.copy(displayName = it.trim().ifBlank { null }))
                },
                label = { Text("Ton prénom") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            ClayDivider()
            Spacer(Modifier.height(20.dp))

            Section("Compte")
            ClaySecondaryButton(
                text = "Se déconnecter",
                icon = Icons.AutoMirrored.Rounded.Logout,
                onClick = { viewModel.logout {} },
            )
            Spacer(Modifier.height(10.dp))
            ClayButton(
                text = "Supprimer mes données",
                icon = Icons.Rounded.Delete,
                tint = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                onClick = { confirmDelete = true },
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "Connecteur : ${viewModel.connectorLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tes identifiants sont chiffrés par le Keystore Android et ne " +
                    "quittent jamais cet appareil. Aucune donnée scolaire n'est " +
                    "envoyée à un serveur tiers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer tes données ?") },
            text = {
                Text(
                    "Le cache local, la session et les identifiants chiffrés de " +
                        "cet appareil seront effacés définitivement.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteLocalData {}
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 10.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ClaySettingRow(title = title, description = description) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(end = 6.dp),
    )
}
