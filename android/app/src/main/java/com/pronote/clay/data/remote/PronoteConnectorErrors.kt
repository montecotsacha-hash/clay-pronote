package com.pronote.clay.data.remote

import com.pronote.clay.core.Logger
import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// ----------------------------------------------------------------- compte

internal object AccountKind {
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

internal class BadCredentialsError : Exception("Identifiants incorrects")
internal class AccessDeniedError : Exception("Accès refusé")
internal class AccountDisabledError : Exception("Compte désactivé")
internal class SecurityRequiredError(message: String = "Double authentification attendue") : Exception(message)
internal class SuspendedIPError : Exception("Adresse IP suspendue")
internal class BusyPageError : Exception("Site momentanément indisponible")
internal class RateLimitedError : Exception("Trop de tentatives de connexion")
internal class PageUnavailableError : Exception("Page demandée introuvable")
internal class SessionExpiredError : Exception("Session expirée")
internal class ServerSideError(detail: String) : Exception("Erreur serveur Pronote : $detail")
internal class ConnectorProtocolError(detail: String, cause: Throwable? = null) : Exception(detail, cause)

