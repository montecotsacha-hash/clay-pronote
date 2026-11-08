package com.pronote.clay.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pronote.clay.data.remote.UpdateStatus
import com.pronote.clay.ui.components.ConfirmDeleteDialog
import com.pronote.clay.ui.components.ClayButton
import com.pronote.clay.ui.components.ClayDivider
import com.pronote.clay.ui.components.ClayReveal
import com.pronote.clay.ui.components.ClaySecondaryButton
import com.pronote.clay.ui.components.ClaySettingRow
import com.pronote.clay.ui.components.ClaySurface
import com.pronote.clay.ui.theme.ClayDimens
import com.pronote.clay.ui.viewmodel.HomeViewModel

/**
 * SettingsScreen — the "Paramètres" section: preferences, account actions and
 * the privacy reminder. It is a real screen (own top bar, own back action)
 * instead of a sheet, so it can be reached from the navigation menu and can
 * never overlap another screen.
 *
 * Each card owns its slice of the preferences: they all observe the same
 * [HomeViewModel.preferences] flow, so a switch still updates the screen as a
 * whole while every card stays readable on its own.
 */
@Composable
fun SettingsScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val updateStatus by viewModel.updateStatus.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        SettingsTopBar(onBack = onBack)

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ClayDimens.ScreenPadding)
                .padding(bottom = 40.dp),
        ) {
            ClayReveal { SyncCard(viewModel) }

            Spacer(Modifier.height(18.dp))

            ClayReveal(index = 1) { AppearanceCard(viewModel) }

            Spacer(Modifier.height(18.dp))

            ClayReveal(index = 2) { SecurityCard(viewModel) }

            Spacer(Modifier.height(18.dp))

            ClayReveal(index = 3) { UpdateCard(viewModel, updateStatus) }

            Spacer(Modifier.height(18.dp))

            ClayReveal(index = 4) { AccountCard(viewModel, onDelete = { confirmDelete = true }) }

            Spacer(Modifier.height(18.dp))

            ClayReveal(index = 5) { PrivacyNote(viewModel) }
        }
    }

    ConfirmDeleteDialog(
        visible = confirmDelete,
        onDismiss = { confirmDelete = false },
        onConfirm = {
            confirmDelete = false
            viewModel.deleteLocalData {}
        },
    )
}

/** Synchronisation automatique et alertes locales. */
@Composable
private fun SyncCard(viewModel: HomeViewModel) {
    val preferences by viewModel.preferences.collectAsState()

    ClaySurface(modifier = Modifier.fillMaxWidth(), depth = 10.dp) {
        Column(Modifier.padding(18.dp)) {
            ClaySettingRow(
                title = "Synchronisation automatique",
                description = "Synchronise périodiquement tes notes quand l'appareil est connecté à Internet.",
            ) {
                Switch(
                    checked = preferences.autoSync,
                    onCheckedChange = {
                        viewModel.updatePreferences(preferences.copy(autoSync = it))
                    },
                )
            }
            ClayDivider()
            ClaySettingRow(
                title = "Nouvelles notes",
                description = "Une alerte locale après une synchronisation en arrière-plan.",
            ) {
                Switch(
                    checked = preferences.notifyNewGrades,
                    onCheckedChange = {
                        viewModel.updatePreferences(preferences.copy(notifyNewGrades = it))
                    },
                )
            }
            ClayDivider()
            ClaySettingRow(
                title = "Changement de moyenne",
                description = "Préviens-moi quand la moyenne générale bouge d'au moins 0,05.",
            ) {
                Switch(
                    checked = preferences.notifyAverageChange,
                    onCheckedChange = {
                        viewModel.updatePreferences(preferences.copy(notifyAverageChange = it))
                    },
                )
            }
        }
    }
}

/** Thème, couleurs dynamiques et prénom affiché. */
@Composable
private fun AppearanceCard(viewModel: HomeViewModel) {
    val preferences by viewModel.preferences.collectAsState()

    ClaySurface(modifier = Modifier.fillMaxWidth(), depth = 10.dp) {
        Column(Modifier.padding(18.dp)) {
            Section("Apparence")
            ClaySettingRow(
                title = "Thème",
                description = "Sombre, clair ou système.",
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
            ClayDivider()
            ClaySettingRow(
                title = "Couleurs Material You",
                description = "Reprends les couleurs de ton fond d'écran (Android 12+).",
            ) {
                Switch(
                    checked = preferences.dynamicColor,
                    onCheckedChange = {
                        viewModel.updatePreferences(preferences.copy(dynamicColor = it))
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = preferences.displayName.orEmpty(),
                onValueChange = {
                    viewModel.updatePreferences(
                        preferences.copy(displayName = it.trim().ifBlank { null }),
                    )
                },
                label = { Text("Prénom à afficher") },
                supportingText = { Text("Remplace le prénom Pronote après « Bonjour ».") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Verrouillage biométrique de l'application. */
@Composable
private fun SecurityCard(viewModel: HomeViewModel) {
    val preferences by viewModel.preferences.collectAsState()

    ClaySurface(modifier = Modifier.fillMaxWidth(), depth = 10.dp) {
        Column(Modifier.padding(18.dp)) {
            Section("Sécurité")
            ClaySettingRow(
                title = "Verrouillage biométrique",
                description = "Empreinte, visage ou code de l'appareil à chaque ouverture de l'app. Nécessite un verrou d'appareil configuré.",
            ) {
                Switch(
                    checked = preferences.biometricLock,
                    onCheckedChange = {
                        viewModel.updatePreferences(preferences.copy(biometricLock = it))
                    },
                )
            }
        }
    }
}

/** Version installée, état de la vérification et téléchargement de l'APK. */
@Composable
private fun UpdateCard(viewModel: HomeViewModel, updateStatus: UpdateStatus) {
    val context = LocalContext.current

    ClaySurface(modifier = Modifier.fillMaxWidth(), depth = 10.dp) {
        Column(Modifier.padding(18.dp)) {
            Section("Mise à jour")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Version installée",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    viewModel.container.installedVersionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            when (val status = updateStatus) {
                is UpdateStatus.Available -> {
                    Text(
                        "Ardoise ${status.info.versionName ?: "plus récente"} " +
                            "est disponible sur le site de distribution.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    ClayButton(
                        text = "Télécharger la mise à jour",
                        onClick = {
                            viewModel.dismissUpdate()
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(status.info.apkUrl),
                                ),
                            )
                        },
                    )
                }
                UpdateStatus.UpToDate -> Text(
                    "Tu as la dernière version publiée.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UpdateStatus.Checking -> Text(
                    "Vérification…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UpdateStatus.Unavailable -> Text(
                    "Impossible de vérifier pour l'instant (hors ligne ?).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UpdateStatus.Idle -> Unit
            }
            Spacer(Modifier.height(10.dp))
            ClaySecondaryButton(
                text = "Vérifier les mises à jour",
                onClick = { viewModel.checkForUpdate() },
            )
        }
    }
}

/** Déconnexion et suppression des données locales. */
@Composable
private fun AccountCard(viewModel: HomeViewModel, onDelete: () -> Unit) {
    ClaySurface(modifier = Modifier.fillMaxWidth(), depth = 10.dp) {
        Column(Modifier.padding(18.dp)) {
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
                onClick = onDelete,
            )
        }
    }
}

/** Rappel de confidentialité, sous les cartes de réglages. */
@Composable
private fun PrivacyNote(viewModel: HomeViewModel) {
    Column {
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

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
        }
        Box(Modifier.weight(1f)) {
            Text(
                "Paramètres",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(end = 6.dp),
    )
}
