package com.pronote.clay.ui

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.pronote.clay.core.Logger

/**
 * BiometricGate — single entry point to the platform biometric prompt.
 *
 * The classes used here only exist on Android 9+ (API 28): every entry point is
 * version-guarded, and every failure falls back to "unavailable" instead of
 * leaving the user locked out. Below API 28 the caller uses the device
 * credential screen instead (see MainActivity).
 */
internal object BiometricGate {

    /** No sensor, or no screen lock enrolled: the caller must not lock the user in. */
    const val ERROR_UNAVAILABLE = 11
    const val ERROR_NO_CREDENTIAL = 12

    private var prompt: BiometricPrompt? = null
    private var cancellation: CancellationSignal? = null

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * @param onFailure called with an error code; [ERROR_UNAVAILABLE] and
     * [ERROR_NO_CREDENTIAL] mean the app should unlock instead of trapping the user.
     */
    fun authenticate(activity: Activity, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        if (!isSupported()) {
            onFailure(ERROR_UNAVAILABLE)
            return
        }
        try {
            val info = BiometricPrompt.Builder(activity)
                .setTitle("Déverrouille Ardoise")
                .setSubtitle("Empreinte, visage ou code de l'appareil")
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setAllowedAuthenticators(
                            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setDeviceCredentialAllowed(true)
                    }
                }
                .build()

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errorCode)
                }

                override fun onAuthenticationFailed() {
                    // Un doigt non reconnu : l'utilisateur retente depuis l'écran.
                }
            }

            cancellation?.cancel()
            val signal = CancellationSignal()
            cancellation = signal
            prompt = info
            info.authenticate(
                signal,
                ContextCompat.getMainExecutor(activity),
                callback,
            )
        } catch (error: Throwable) {
            // Le prompt système est indisponible (matériel absent, ROM
            // propriétaire…) : on préfère déverrouiller plutôt que d'enfermer.
            Logger.warn("Biometric prompt unavailable: ${error.javaClass.simpleName}")
            onFailure(ERROR_UNAVAILABLE)
        }
    }

    fun cancel() {
        runCatching { cancellation?.cancel() }
        cancellation = null
        prompt = null
    }

    /** True when the device can protect the app (PIN, mot de passe, schéma ou biométrie). */
    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true
}
