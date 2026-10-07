package com.pronote.clay

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pronote.clay.ui.AppRoot
import com.pronote.clay.ui.BiometricGate
import com.pronote.clay.ui.screens.LockScreen
import com.pronote.clay.ui.viewmodel.HomeViewModel

/**
 * MainActivity — the single Compose entry point.
 *
 * It wires the composition root to the HomeViewModel, applies the theme from
 * the user preferences and routes between the login and home screens. A
 * POST_NOTIFICATIONS runtime request is issued on Android 13+ so local
 * "nouvelle note" notifications can actually be posted.
 *
 * When the optional biometric lock is enabled, [LockScreen] REPLACES the app:
 * nothing behind it is composed, and the app re-locks as soon as it goes to
 * the background.
 */
class MainActivity : ComponentActivity() {

    /** true = the lock screen replaces the app (set from the user preferences). */
    private val locked = mutableStateOf(false)

    /** Device-credential fallback for Android 7 and 8 (no platform BiometricPrompt). */
    private val credentialLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) locked.value = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PronoteApp).container

        // The lock is opt-in AND only when the device itself is protected:
        // never trap a user who has no PIN/biometrics enrolled.
        val mustLock = container.preferences.load().biometricLock &&
            BiometricGate.isDeviceSecure(this)
        val unlockedBeforeRotation = savedInstanceState?.getBoolean(KEY_UNLOCKED, false) == true
        locked.value = mustLock && !unlockedBeforeRotation

        setContent {
            val viewModel: HomeViewModel = viewModel { HomeViewModel(container) }
            val isLocked by locked

            LaunchedEffect(isLocked) {
                if (isLocked) requestUnlock()
            }

            if (isLocked) {
                LockScreen(onUnlock = { requestUnlock() })
            } else {
                AppRoot(viewModel)
                NotificationPermissionRequest()
            }
        }
    }

    /** Asks for biometrics (Android 9+) or the device credential (older). */
    private fun requestUnlock() {
        if (BiometricGate.isSupported()) {
            BiometricGate.authenticate(
                activity = this,
                onSuccess = { runOnUiThread { locked.value = false } },
                onFailure = { code ->
                    // No sensor / no screen lock: unlock rather than trap the user.
                    if (code == BiometricGate.ERROR_UNAVAILABLE ||
                        code == BiometricGate.ERROR_NO_CREDENTIAL
                    ) {
                        runOnUiThread { locked.value = false }
                    }
                },
            )
            return
        }

        val intent = getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent("Déverrouille Ardoise", null)
        if (intent == null) {
            locked.value = false
        } else {
            credentialLauncher.launch(intent)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            // Backgrounded: lock again (a rotation must not re-ask, hence the guard).
            BiometricGate.cancel()
            val container = (application as PronoteApp).container
            if (container.preferences.load().biometricLock) locked.value = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UNLOCKED, !locked.value)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        BiometricGate.cancel()
        super.onDestroy()
    }

    companion object {
        private const val KEY_UNLOCKED = "unlocked"
    }
}

/** Android 13+ needs the runtime notification permission for local alerts. */
@androidx.compose.runtime.Composable
private fun NotificationPermissionRequest() {
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
