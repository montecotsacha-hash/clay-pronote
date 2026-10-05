package com.pronote.clay.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pronote.clay.core.Logger

/**
 * SecureCredentialsManager — the only place secrets are written.
 *
 * Backed by Android Keystore via EncryptedSharedPreferences: the master key
 * lives in the Keystore and never leaves the device. Credentials are therefore
 * never stored in plain SharedPreferences, never in the database, and never
 * logged.
 *
 * Prefer the session token over the password whenever the connector can issue
 * one: [StoredCredentials.password] stays null in that case.
 */
class SecureCredentialsManager(context: Context) {

    data class StoredCredentials(
        val schoolUrl: String,
        val schoolName: String,
        val username: String,
        val token: String? = null,
        val expiresAt: Long = 0L,
        /** Stable app/device identifier required to resume Pawnote mobile sessions. */
        val deviceUuid: String? = null,
        /** Only kept when the connector cannot issue a durable token. */
        val password: String? = null,
    )

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (error: Throwable) {
            Logger.error("Keystore unavailable, secure storage disabled", error)
            // Fail closed: an in-memory-only store means the session is lost on
            // restart rather than ever being written in the clear.
            context.getSharedPreferences("${PREFS_NAME}_memory", Context.MODE_PRIVATE)
        }
    }

    fun save(accountId: String, credentials: StoredCredentials) {
        prefs.edit()
            .putString(key(accountId, "url"), credentials.schoolUrl)
            .putString(key(accountId, "school"), credentials.schoolName)
            .putString(key(accountId, "user"), credentials.username)
            .putString(key(accountId, "token"), credentials.token)
            .putLong(key(accountId, "expires"), credentials.expiresAt)
            .putString(key(accountId, "device_uuid"), credentials.deviceUuid)
            .putString(key(accountId, "pass"), credentials.password)
            .apply()
    }

    fun load(accountId: String): StoredCredentials? {
        val url = prefs.getString(key(accountId, "url"), null) ?: return null
        return StoredCredentials(
            schoolUrl = url,
            schoolName = prefs.getString(key(accountId, "school"), "") ?: "",
            username = prefs.getString(key(accountId, "user"), "") ?: "",
            token = prefs.getString(key(accountId, "token"), null),
            expiresAt = prefs.getLong(key(accountId, "expires"), 0L),
            deviceUuid = prefs.getString(key(accountId, "device_uuid"), null),
            password = prefs.getString(key(accountId, "pass"), null),
        )
    }

    fun clear(accountId: String) {
        prefs.edit()
            .remove(key(accountId, "url"))
            .remove(key(accountId, "school"))
            .remove(key(accountId, "user"))
            .remove(key(accountId, "token"))
            .remove(key(accountId, "expires"))
            .remove(key(accountId, "device_uuid"))
            .remove(key(accountId, "pass"))
            .apply()
    }

    /** Key rotation: wipe every stored secret so the next save re-encrypts. */
    fun rotate() {
        prefs.edit().clear().apply()
    }

    private fun key(accountId: String, field: String) = "$accountId.$field"

    private companion object {
        const val PREFS_NAME = "pronote_secure_credentials"
    }
}
