package com.pronote.clay.data.remote

import com.pronote.clay.core.Logger
import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Connexion directe à Pronote, implémentée sur l'appareil d'après le protocole
 * public documenté par les wrappers open source (Pawnote — GPL-3.0-or-later,
 * pronotepy — MIT) et adapté aux serveurs PRONOTE 2024→2026.
 *
 * Spécificités 2026 découvertes et gérées ici :
 *  - la page à interroger est `mobile.{eleve,parent,professeur}.html` avec un
 *    User-Agent de l'application mobile PRONOTE (sans lui, le serveur renvoie
 *    une page « navigateur incompatible » sans données de session) ;
 *  - le marqueur de session est `Start ( {...} );}` ; le parseur tolère les
 *    espaces et la parenthèse `)` finale ; l'ancien parseur cherchait
 *    literallement `)}catch` et échouait, d'où le faux message « lien
 *    d'établissement invalide » ;
 *  - les numéros d'ordre des requêtes vont de 2 en 2 (1, 3, 5, …) ;
 *    l'ordre 1 est chiffré avec un IV zéro (16 octets nuls), les suivants
 *    avec l'IV réel ;
 *  - l'IV AES réel vaut MD5(ivTemp) ; la clé initiale vaut MD5("") ;
 *    l'Uuid transmis = IV brut en base64 quand MR/ER sont absents ou
 *    start.http = true ; Uuid chiffré RSA seulement si MR ou ER présents
 *    et start.http = false ;
 *  - la clé AES de session arrive dans `Authentification → cle` ;
 *    elle se déchiffre avec la clé du challenge, puis les codes virgulés
 *    deviennent les octets bruts de la clé (pronotepy `_enBytes`) ;
 *    la clé de session = MD5(de ces octets bruts) ;
 *  - sur les serveurs ≥ 2025.1.3 sans CrA/CoA, chiffrement et compression
 *    sont désactivés ; `dataSec` circule en objet JSON `{ "data": … }` ;
 *  - le challenge 2026 circule en clair et est chiffré tel quel ;
 *    la transformation « déchiffrer + 1 char sur 2 » du protocole Pawnote
 *    produit du charabia sur Pronote 2026 (ne pas l'appliquer) ;
 *  - pour le flux login avec jeton (compte de l'application mobile),
 *    cookie `appliMobile=1` sur la requête GET de page et les champs
 *    `enConnexionAppliMobile` / `demandeConnexionAppliMobile` sont inversés ;
 *    la clé challenge = MD5(user + SHA256(alea + jeton) hex MAJ) ;
 *    l'alea de la réponse Identification est inclus dans la dérivation.
 */
class PronoteDirectDataSource : PronoteDataSource {

    override val strategy: String = "Connexion directe — protocole PRONOTE"

    private data class ActiveSession(
        val refreshToken: String,
        val session: PronoteSession,
    )

    private val sessions = java.util.concurrent.ConcurrentHashMap<String, ActiveSession>()

    override suspend fun authenticate(
        schoolUrl: String,
        username: String,
        password: String,
    ): PronoteResult<PronoteAuthResult> = withContext(Dispatchers.IO) {
        val deviceUuid = java.util.UUID.randomUUID().toString()
        try {
            val session = PronoteSession.open(schoolUrl, AccountKind.STUDENT, tokenFlow = false)
            val login = session.loginWithCredentials(username, password, deviceUuid)
            val payload = session.readSnapshot()
            sessions[sessionKey(schoolUrl, username)] = ActiveSession(login.refreshToken, session)
            PronoteResult.Success(
                PronoteAuthResult(
                    token = login.refreshToken,
                    expiresAt = Long.MAX_VALUE,
                    schoolName = session.userResource.establishmentName,
                    payload = payload,
                    deviceUuid = deviceUuid,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PronoteResult.Failure(mapError(error))
        }
    }

    override suspend fun restoreSession(
        token: String,
        schoolUrl: String,
        username: String,
        deviceUuid: String,
    ): PronoteResult<PronoteAuthResult> = withContext(Dispatchers.IO) {
        connectWithToken(token, schoolUrl, username, deviceUuid)
    }

    override suspend fun fetchAll(
        token: String,
        schoolUrl: String,
        username: String,
        deviceUuid: String,
    ): PronoteResult<PronoteRawPayload> = withContext(Dispatchers.IO) {
        val active = sessions[sessionKey(schoolUrl, username)]?.takeIf { it.refreshToken == token }
        if (active != null) {
            try {
                PronoteResult.Success(active.session.readSnapshot())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                PronoteResult.Failure(mapError(error))
            }
        } else {
            when (val restored = connectWithToken(token, schoolUrl, username, deviceUuid)) {
                is PronoteResult.Success -> PronoteResult.Success(restored.value.payload)
                is PronoteResult.Failure -> restored
            }
        }
    }

    override suspend fun logout(token: String) {
        sessions.entries.removeAll { (_, session) -> session.refreshToken == token }
    }

    private suspend fun connectWithToken(
        token: String,
        schoolUrl: String,
        username: String,
        deviceUuid: String,
    ): PronoteResult<PronoteAuthResult> = try {
        val session = PronoteSession.open(schoolUrl, AccountKind.STUDENT, tokenFlow = true)
        val login = session.loginWithToken(username, token, deviceUuid)
        val payload = session.readSnapshot()
        sessions[sessionKey(schoolUrl, username)] = ActiveSession(token, session)
        PronoteResult.Success(
            PronoteAuthResult(
                token = token,
                expiresAt = Long.MAX_VALUE,
                schoolName = session.userResource.establishmentName,
                payload = payload,
                deviceUuid = deviceUuid,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        PronoteResult.Failure(mapError(error))
    }

    private fun sessionKey(schoolUrl: String, username: String) = "$schoolUrl|$username"

    private fun mapError(error: Throwable): PronoteError {
        Logger.error("Erreur du connecteur Pronote", error)
        return when (error) {
            is BadCredentialsError -> PronoteError.InvalidCredentials
            is SecurityRequiredError -> PronoteError.CaptchaRequired
            is SuspendedIPError, is BusyPageError, is RateLimitedError -> PronoteError.PronoteUnavailable
            is AccessDeniedError, is AccountDisabledError -> PronoteError.InvalidCredentials
            is PageUnavailableError -> PronoteError.InvalidPronoteUrl
            is SessionExpiredError -> PronoteError.SessionExpired
            is ServerSideError -> PronoteError.ServerError
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is javax.net.ssl.SSLException,
            is java.io.IOException -> PronoteError.NetworkUnavailable
            is ConnectorProtocolError -> PronoteError.ParsingError
            else -> PronoteError.UnknownError
        }
    }
}

// ----------------------------------------------------------------- compte

private object AccountKind {
    const val STUDENT = 6
    const val PARENT = 7
    const val TEACHER = 8

    fun path(kind: Int): String = when (kind) {
        STUDENT -> "eleve"
        PARENT -> "parent"
        TEACHER -> "professeur"
        else -> "eleve"
    }
}

// ----------------------------------------------------------------- erreurs

private class BadCredentialsError : Exception("Identifiants incorrects")
private class AccessDeniedError : Exception("Accès refusé")
private class AccountDisabledError : Exception("Compte désactivé")
private class SecurityRequiredError(message: String = "Double authentification attendue") : Exception(message)
private class SuspendedIPError : Exception("Adresse IP suspendue")
private class BusyPageError : Exception("Site momentanément indisponible")
private class RateLimitedError : Exception("Trop de tentatives de connexion")
private class PageUnavailableError : Exception("Page demandée introuvable")
private class SessionExpiredError : Exception("Session expirée")
private class ServerSideError(detail: String) : Exception("Erreur serveur Pronote : $detail")
private class ConnectorProtocolError(detail: String, cause: Throwable? = null) : Exception(detail, cause)

