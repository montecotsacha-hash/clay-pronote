package com.pronote.clay.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pronote.clay.data.remote.UpdateStatus
import com.pronote.clay.ui.components.ClayDivider
import com.pronote.clay.ui.components.ClaySurface
import com.pronote.clay.ui.components.ConfirmDeleteDialog
import com.pronote.clay.ui.components.UpdateAvailableDialog
import com.pronote.clay.ui.components.clay
import com.pronote.clay.ui.screens.HomeScreen
import com.pronote.clay.ui.screens.SettingsScreen
import com.pronote.clay.ui.screens.TimetableScreen
import com.pronote.clay.ui.theme.ClayDimens
import com.pronote.clay.ui.viewmodel.HomeViewModel
import com.pronote.clay.ui.viewmodel.TimetableViewModel
import kotlinx.coroutines.launch

/**
 * The top-level sections reachable from the navigation menu.
 *
 * Previously the timetable was pushed as an extra layer ON TOP of the
 * dashboard, so both screens were composed at once and the schedule ended up
 * hidden behind the home content. Sections now replace each other: exactly one
 * is on screen at any time.
 */
enum class AppSection(val label: String, val icon: ImageVector) {
    Dashboard("Tableau de bord", Icons.Rounded.Home),
    Timetable("Emploi du temps", Icons.Rounded.CalendarMonth),
    Settings("Paramètres", Icons.Rounded.Settings),
}

/**
 * AppShell — the authenticated surface: a clay navigation menu plus the section
 * it selects. It owns the only navigation state of the app, so a screen can
 * never be drawn under another one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(viewModel: HomeViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(AppSection.Dashboard) }

    fun goTo(target: AppSection) {
        section = target
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ClayMenu(section = section, viewModel = viewModel, onSelect = ::goTo)
        },
    ) {
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                (
                    slideInHorizontally(tween(300)) { if (forward) it / 6 else -it / 6 } +
                        fadeIn(tween(240))
                    ) togetherWith (
                    slideOutHorizontally(tween(300)) { if (forward) -it / 6 else it / 6 } +
                        fadeOut(tween(160))
                    )
            },
            label = "section",
        ) { target ->
            when (target) {
                AppSection.Dashboard -> HomeScreen(
                    viewModel = viewModel,
                    onOpenMenu = { scope.launch { drawerState.open() } },
                )

                AppSection.Timetable -> {
                    // Activity-scoped on purpose: swiping between days and weeks
                    // never refetches after the first load. Fully qualified
                    // because the `viewModel` parameter shadows the factory.
                    val timetableViewModel: TimetableViewModel =
                        androidx.lifecycle.viewmodel.compose.viewModel {
                            TimetableViewModel(viewModel.container)
                        }
                    TimetableScreen(
                        viewModel = timetableViewModel,
                        onBack = { goTo(AppSection.Dashboard) },
                    )
                }

                AppSection.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { goTo(AppSection.Dashboard) },
                )
            }
        }

        // La proposition de mise à jour du site de distribution, au-dessus de
        // la section courante : elle ne disparaît pas en changeant d'écran.
        val updateStatus by viewModel.updateStatus.collectAsState()
        val currentUpdate = updateStatus
        if (currentUpdate is UpdateStatus.Available && currentUpdate.promptVisible) {
            val context = LocalContext.current
            UpdateAvailableDialog(
                info = currentUpdate.info,
                onDownload = {
                    viewModel.dismissUpdate()
                    // Le navigateur télécharge l'APK : Android l'installera
                    // par-dessus la version actuelle, données conservées.
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(currentUpdate.info.apkUrl)),
                    )
                },
                onDismiss = { viewModel.dismissUpdate() },
            )
        }
    }
}

/**
 * The clay drawer: every section, the theme switch, the account actions, plus
 * the privacy reminder.
 *
 * This drawer is the ONLY way to reach the settings, the timetable and the
 * account actions: the dashboard carries no shortcut to them, so there is
 * exactly one path to each destination.
 */
@Composable
private fun ClayMenu(
    section: AppSection,
    viewModel: HomeViewModel,
    onSelect: (AppSection) -> Unit,
) {
    val preferences by viewModel.preferences.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val flipTheme = {
        viewModel.updatePreferences(preferences.copy(darkTheme = preferences.darkTheme != true))
    }
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ClayDimens.ScreenPadding)
                .padding(top = 28.dp, bottom = 30.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clay(
                            depth = 10.dp,
                            shape = RoundedCornerShape(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        ),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Ardoise",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Menu",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(26.dp))

            AppSection.entries.forEach { item ->
                MenuItem(
                    item = item,
                    selected = item == section,
                    onClick = { onSelect(item) },
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(14.dp))
            ClayDivider()
            Spacer(Modifier.height(16.dp))

            MenuRow(
                icon = Icons.Rounded.DarkMode,
                label = "Mode sombre",
                trailing = {
                    Switch(
                        checked = preferences.darkTheme == true,
                        onCheckedChange = { flipTheme() },
                    )
                },
                onClick = flipTheme,
            )
            Spacer(Modifier.height(10.dp))
            MenuRow(
                icon = Icons.AutoMirrored.Rounded.Logout,
                label = "Se déconnecter",
                onClick = { viewModel.logout {} },
            )
            Spacer(Modifier.height(10.dp))
            MenuRow(
                icon = Icons.Rounded.Delete,
                label = "Supprimer mes données",
                iconTint = MaterialTheme.colorScheme.error,
                labelColor = MaterialTheme.colorScheme.error,
                onClick = { confirmDelete = true },
            )

            Spacer(Modifier.height(14.dp))
            ClayDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                "Tes identifiants ne quittent jamais cet appareil : la connexion " +
                    "est directe vers le serveur de ton établissement.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

/** A menu entry: the same clay card as a section, with an optional trailing control. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    ClaySurface(
        modifier = Modifier.fillMaxWidth(),
        depth = 4.dp,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun MenuItem(
    item: AppSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ClaySurface(
        modifier = Modifier.fillMaxWidth(),
        depth = if (selected) 12.dp else 4.dp,
        shape = RoundedCornerShape(20.dp),
        tint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else null,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                item.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
