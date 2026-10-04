package com.pronote.clay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pronote.clay.ui.AppRoot
import com.pronote.clay.ui.viewmodel.HomeViewModel

/**
 * MainActivity — the single Compose entry point.
 *
 * It wires the composition root to the HomeViewModel, applies the theme from
 * the user preferences and routes between the login and home screens. A
 * POST_NOTIFICATIONS runtime request is issued on Android 13+ so local
 * "nouvelle note" notifications can actually be posted.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PronoteApp).container

        setContent {
            val viewModel: HomeViewModel = viewModel { HomeViewModel(container) }
            AppRoot(viewModel)

            val context = LocalContext.current
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* denied: the app simply never posts local notifications */ }

            LaunchedEffect(Unit) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
