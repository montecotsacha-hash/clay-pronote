package com.pronote.clay.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pronote.clay.notifications.LocalNotifications
import com.pronote.clay.ui.components.clay
import com.pronote.clay.ui.screens.LoginScreen
import com.pronote.clay.ui.theme.AppTheme
import com.pronote.clay.ui.viewmodel.HomeEvent
import com.pronote.clay.ui.viewmodel.HomeUiState
import com.pronote.clay.ui.viewmodel.HomeViewModel

/**
 * AppRoot — the top of the Compose tree.
 *
 * Collects the ViewModel state, posts local notifications produced by syncs and
 * routes between Loading / Login / Home. The theme follows the user
 * preferences (dark theme + Material You opt-in).
 */
@Composable
fun AppRoot(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val event by viewModel.events.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(event) {
        when (val current = event) {
            is HomeEvent.Notify -> {
                LocalNotifications.notify(
                    context,
                    current.notification.title,
                    current.notification.body,
                )
                viewModel.consumeEvent()
            }
            is HomeEvent.Message -> {
                Toast.makeText(context, current.text, Toast.LENGTH_LONG).show()
                viewModel.consumeEvent()
            }
            null -> Unit
        }
    }

    AppTheme(darkTheme = preferences.darkTheme, dynamicColor = preferences.dynamicColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val current = state) {
                HomeUiState.Loading -> FullScreenLoader()
                HomeUiState.Unauthenticated ->
                    LoginScreen(viewModel = viewModel)
                is HomeUiState.Error ->
                    if (current.snapshot == null) {
                        // A failed login has no local data to show: back to the form.
                        LoginScreen(viewModel = viewModel)
                    } else {
                        AppShell(viewModel)
                    }
                is HomeUiState.Success -> AppShell(viewModel)
                is HomeUiState.Offline -> AppShell(viewModel)
                is HomeUiState.SyncIssue -> AppShell(viewModel)
            }
        }
    }
}

@Composable
private fun FullScreenLoader() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(46.dp)
                    .clay(depth = 12.dp, tint = MaterialTheme.colorScheme.primary),
            )
            Column(Modifier.padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Connexion à Pronote…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}
