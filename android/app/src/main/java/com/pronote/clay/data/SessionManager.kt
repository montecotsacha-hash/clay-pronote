package com.pronote.clay.data

import com.pronote.clay.core.Logger
import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import com.pronote.clay.data.remote.PronoteAuthResult
import com.pronote.clay.data.remote.PronoteCompatibilityLayer
import com.pronote.clay.data.security.SecureCredentialsManager
import com.pronote.clay.domain.PronoteSession

/**
 * SessionManager — owns the Pronote session lifecycle.
 *
 * restoreSession() rehydrates from the encrypted vault, with a two-step
 * reconnection chain: mobile token first, then the stored password.
 * isSessionValid() is a cheap expiry check, never a network call.
 * refreshSession() tries the chain above; null only when everything failed.
 * clearSession()   wipes the vault entry for the account.
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
     * Rehydrate a session from the vault.
     *
     * 1. Reconnexion avec le jeton mobile PRONOTE (rapide, une seule requête).
     * 2. Secours : le jeton a été invalidé côté serveur (autre appareil, purge
     *    des sessions, uuid d'appareil différent…). Le mot de passe chiffré
     *    dans le vault permet de re-fabriquer un jeton sans rien demander à
     *    l'utilisateur — la reconnexion doit être invisible.
     *
     * The password stays in the vault after a token login: it is the safety
     * net that makes every future session loss self-healing, so it is never
     * downgraded to null while the account remains active.
     */
    suspend fun restoreSession(accountId: String): PronoteResult<PronoteSession> {
        val stored = vault.load(accountId) ?: return PronoteResult.Failure(PronoteError.SessionExpired)

        stored.token?.let { token ->
            val restored = connector.restoreSession(
                token = token,
                schoolUrl = stored.schoolUrl,
                username = stored.username,
                deviceUuid = stored.deviceUuid.orEmpty(),
            )
            if (restored is PronoteResult.Success) return commit(accountId, stored, restored.value)
        }

        stored.password?.let { password ->
            val reauth = connector.authenticate(stored.schoolUrl, stored.username, password)
            if (reauth is PronoteResult.Success) return commit(accountId, stored, reauth.value)
        }

        Logger.warn("Session restore failed (token and password)")
        return PronoteResult.Failure(PronoteError.SessionExpired)
    }

    /**
     * Enregistre la session obtenue par une reconnexion et la transmet à
     * l'appelant — le seul endroit qui décide ce qui est gardé dans le vault.
     *
     * Un login par identifiants ne renvoie pas toujours de jeton mobile : ne
     * jamais écraser un jeton valide par une valeur vide, sinon la reconnexion
     * suivante part avec un jeton vide et échoue en « mauvais mot de passe ».
     */
    private fun commit(
        accountId: String,
        stored: SecureCredentialsManager.StoredCredentials,
        auth: PronoteAuthResult,
    ): PronoteResult<PronoteSession> {
        vault.save(
            accountId,
            stored.copy(
                token = auth.token.takeIf { it.isNotBlank() } ?: stored.token,
                schoolName = auth.schoolName,
                expiresAt = auth.expiresAt,
                deviceUuid = auth.deviceUuid ?: stored.deviceUuid,
            ),
        )
        return PronoteResult.Success(
            PronoteSession(
                token = auth.token,
                expiresAt = auth.expiresAt,
                schoolUrl = stored.schoolUrl,
                schoolName = auth.schoolName,
                connected = true,
            ),
        )
    }

    /** Attempt a refresh; null when the session cannot be salvaged. */
    suspend fun refreshSession(accountId: String): PronoteSession? =
        when (val result = restoreSession(accountId)) {
            is PronoteResult.Success -> result.value
            is PronoteResult.Failure -> null
        }
}
