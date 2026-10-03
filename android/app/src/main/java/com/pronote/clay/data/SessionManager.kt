package com.pronote.clay.data

import com.pronote.clay.core.Logger
import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import com.pronote.clay.data.remote.PronoteCompatibilityLayer
import com.pronote.clay.data.security.SecureCredentialsManager
import com.pronote.clay.domain.PronoteSession

/**
 * SessionManager — owns the Pronote session lifecycle.
 *
 * restoreSession() rehydrates from the encrypted vault
 * isSessionValid() is a cheap expiry check, never a network call
 * refreshSession() tries the connector, else surfaces SessionExpired so the UI
 *                  can ask the user to reconnect while keeping local data
 * clearSession()   wipes the vault entry for the account
 */
class SessionManager(
    private val connector: PronoteCompatibilityLayer,
    private val vault: SecureCredentialsManager,
) {

    fun save(accountId: String, credentials: SecureCredentialsManager.StoredCredentials) {
        vault.save(accountId, credentials)
    }

    fun load(accountId: String): SecureCredentialsManager.StoredCredentials? = vault.load(accountId)

    fun clearSession(accountId: String) = vault.clear(accountId)

    /** A session is valid when a token exists and has not expired. */
    fun isSessionValid(session: PronoteSession?, skewMs: Long = 30_000L): Boolean {
        if (session == null || !session.connected || session.token.isEmpty()) return false
        return session.expiresAt - skewMs > System.currentTimeMillis()
    }

    /**
     * Rehydrate a session from the vault. Prefers the persistent token; falls
     * back to re-authenticating with the password only when the connector could
     * not issue a token.
     */
    suspend fun restoreSession(accountId: String): PronoteResult<PronoteSession> {
        val stored = vault.load(accountId) ?: return PronoteResult.Failure(PronoteError.SessionExpired)

        stored.token?.let { token ->
            return when (val result = connector.restoreSession(token, stored.schoolUrl)) {
                is PronoteResult.Success -> PronoteResult.Success(
                    PronoteSession(
                        token = result.value.token,
                        expiresAt = result.value.expiresAt,
                        schoolUrl = stored.schoolUrl,
                        schoolName = result.value.schoolName,
                        connected = true,
                    ),
                )
                is PronoteResult.Failure -> result
            }
        }

        stored.password?.let { password ->
            return when (
                val result = connector.authenticate(stored.schoolUrl, stored.username, password)
            ) {
                is PronoteResult.Success -> PronoteResult.Success(
                    PronoteSession(
                        token = result.value.token,
                        expiresAt = result.value.expiresAt,
                        schoolUrl = stored.schoolUrl,
                        schoolName = result.value.schoolName,
                        connected = true,
                    ),
                )
                is PronoteResult.Failure -> result
            }
        }

        Logger.warn("Session restore failed")
        return PronoteResult.Failure(PronoteError.SessionExpired)
    }

    /** Attempt a refresh; null when the session cannot be salvaged. */
    suspend fun refreshSession(accountId: String): PronoteSession? =
        when (val result = restoreSession(accountId)) {
            is PronoteResult.Success -> result.value
            is PronoteResult.Failure -> null
        }
}
