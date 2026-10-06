package com.pronote.clay.data.remote

import android.util.Base64
import com.pronote.clay.core.Logger
import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Connexion directe à Pronote, implémentée sur l'appareil d'après le protocole
 * public documenté par les wrappers open source (Pawnote — GPL-3.0-or-later,
 * pronotepy — MIT) et adaptée aux serveurs PRONOTE 2024→2026.
 *
 * Spécificités 2026 découvertes et gérées ici :
 *  - la page à interroger est `mobile.{eleve,parent,professeur}.html` avec un
 *    User-Agent de l'application mobile PRONOTE (sans lui, le serveur renvoie
 *    une page « navigateur incompatible » sans données de session) ;
 *  - le marqueur de session est écrit `Start ( {...} );} catch (e) …` : il faut
 *    tolérer les espaces et la parenthèse avant le `catch` (l'ancien parseur
 *    cherchait littéralement `)}catch` et échouait d'où le faux
 *    « adresse d'établissement invalide ») ;
 *  - les numéros d'ordre des requêtes vont de 2 en 2 (1, 3, 5, …) ;
 *  - l'IV AES réel vaut MD5(IV temporaire) et la clé initiale vaut MD5("")
 *    (l'IV temporaire reste envoyé côté serveur via `Uuid`, chiffré RSA ou
 *    encodé brut en base64 quand la page ne fournit pas de modulus) ;
 *  - la clé AES de session arrive dans `Authentification` → `cle`
 *    (déchiffrée avec la clé du challenge, puis MD5) ;
 *  - sur les serveurs ≥ 2025.1.3, les sessions « allégées » désactivent
 *    chiffrement et compression (`CrA`/`CoA` absents) et `dataSec` circule en
 *    objet JSON `{ "data": … }`.
 */
class PronoteDirectDataSource : PronoteDataSource {

    override val strategy: String = "Connexion directe — protocole PRONOTE"

    /** Live sessions are process-local; only Pawnote-style refresh tokens persist. */
    private data class ActiveSession(val refreshToken: String, val session: PronoteSession)

    private val sessions = java.util.concurrent.ConcurrentHashMap<String, ActiveSession>()

    override suspend fun authenticate(
        schoolUrl: String,
        username: String,
        password: String,
    ): PronoteResult<PronoteAuthResult> = withContext(Dispatchers.IO) {
        val deviceUuid = java.util.UUID.randomUUID().toString()
        try {
            val session = PronoteSession.open(schoolUrl, AccountKind.STUDENT)
            val login = session.loginWithCredentials(username, password, deviceUuid)
            val payload = session.readSnapshot()
            sessions[sessionKey(schoolUrl, username)] = ActiveSession(login.refreshToken, session)
            PronoteResult.Success(
                PronoteAuthResult(
                    token = login.refreshToken,
                    expiresAt = Long.MAX_VALUE,
                    schoolName = session.userResource!!.establishmentName,
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
        val session = PronoteSession.open(schoolUrl, AccountKind.STUDENT)
        session.loginWithToken(username, token, deviceUuid)
        val payload = session.readSnapshot()
        sessions[sessionKey(schoolUrl, username)] = ActiveSession(token, session)
        PronoteResult.Success(
            PronoteAuthResult(
                token = token,
                expiresAt = Long.MAX_VALUE,
                schoolName = session.userResource!!.establishmentName,
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

// ---------------------------------------------------------------------------
// Account kinds
// ---------------------------------------------------------------------------

internal object AccountKind {
    const val STUDENT = 6
    const val PARENT = 7
    const val TEACHER = 8

    fun path(kind: Int) = when (kind) {
        STUDENT -> "eleve"
        PARENT -> "parent"
        TEACHER -> "professeur"
        else -> "eleve"
    }
}

// ---------------------------------------------------------------------------
// Typed protocol errors (mapped to user-facing PronoteError in the data source)
// ---------------------------------------------------------------------------

internal class BadCredentialsError : Exception("Identifiants incorrects")
internal class AccessDeniedError : Exception("Accès refusé")
internal class AccountDisabledError : Exception("Compte désactivé")
internal class SecurityRequiredError : Exception("Sécurité à personnaliser (double authentification)")
internal class SuspendedIPError : Exception("Adresse IP suspendue")
internal class BusyPageError : Exception("Site momentanément indisponible")
internal class RateLimitedError : Exception("Trop de tentatives de connexion")
internal class PageUnavailableError : Exception("Page demandée introuvable")
internal class SessionExpiredError : Exception("Session expirée")
internal class ServerSideError(detail: String) : Exception("Erreur serveur Pronote : $detail")

/** Local protocol failure (unexpected payload, decode error, …). */
internal class ConnectorProtocolError(detail: String, cause: Throwable? = null) : Exception(detail, cause)

// ---------------------------------------------------------------------------
// Session-level protocol implementation
// ---------------------------------------------------------------------------

internal class PronoteSession private constructor(
    val baseUrl: String,
    val serverVersion: List<Int>,
    private val sessionInfo: JSONObject,
) {
    companion object {
        /** Exact User-Agent used by the official PRONOTE mobile app (2026). */
        private const val PRONOTE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Mobile/15E148 PRONOTE Mobile APP Version/2.0.11"

        /** Fixed device GUID historically embedded in the mobile app request. */
        private const val BYDLG = "A6ABB224-12DD-4E31-AD3E-8A39A1C2C335"

        private const val HTTP_TIMEOUT_MS = 20_000

        /** Fallback RSA modulus used by the web client when the page omits MR/ER. */
        private const val FALLBACK_MODULUS =
            "B99B77A3D72D3A29B4271FC7B7300E2F791EB8948174BE7B8024667E915446D4EEA0C2424B8D1EBF7E2DDFF94691C6E994E839225C627D140A8F1146D1B0B5F18A09BBD3D8F421CA1E3E4796B301EEBCCF80D81A32A1580121B8294433C38377083C5517D5921E8A078CDC019B15775292EFDA2C30251B1CCABE812386C893E5"
        private const val FALLBACK_EXPONENT =
            "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010001"

        /** Opens a mobile session for the given school URL and account kind. */
        fun open(rawUrl: String, kind: Int): PronoteSession {
            val baseUrl = cleanUrl(rawUrl)
            val pageUrl = "$baseUrl/mobile.${AccountKind.path(kind)}.html" +
                "?fd=1&login=true&bydlg=$BYDLG"
            val html = httpGet(pageUrl)
            val version = parseVersion(html) ?: throw PageUnavailableError()
            val start = parseStart(html)
            return PronoteSession(baseUrl, version, start).also { it.info.accountKind = kind }
        }

        /** Normalises any Pronote URL (keep host + path, drop .html page / query / fragment). */
        fun cleanUrl(raw: String): String = try {
            val url = URL(raw)
            val path = url.path.orEmpty().split('/').filter { it.isNotEmpty() }.toMutableList()
            if (path.isNotEmpty() && path.last().contains(".html")) path.removeAt(path.lastIndex)
            val base = "${url.protocol}://${url.host}" +
                (if (url.port != -1 && url.port != url.defaultPort) ":${url.port}" else "")
            if (path.isEmpty()) base else "$base/${path.joinToString("/")}"
        } catch (error: Exception) {
            throw PageUnavailableError().initCause(error)
        }

        private fun httpGet(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", PRONOTE_UA)
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw PageUnavailableError()
                return connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } finally {
                connection.disconnect()
            }
        }

        /** Extracts `>PRONOTE 20xx.y.z` from the page. */
        private fun parseVersion(html: String): List<Int>? {
            val match = Regex(">PRONOTE (\\d+\\.\\d+\\.\\d+)").find(html) ?: return null
            return match.groupValues[1].split('.').map { it.toInt() }
        }

        /**
         * Extracts the session arguments from `Start ( {...} );} catch (e) …`.
         * Tolerates whitespace/newlines and the `)` before `catch` — the 2026
         * pages no longer contain Pawnote 1.0.0's literal `)}catch` marker.
         */
        private fun parseStart(html: String): JSONObject {
            if (html.contains("Votre adresse IP est provisoirement suspendue")) throw SuspendedIPError()
            if (html.contains("Le site est momentanément indisponible")) throw BusyPageError()
            if (html.contains("Le site n'est pas disponible")) throw PageUnavailableError()
            val match = Regex("""Start\s*\(\s*(\{[^)]*\})""").find(html) ?: throw PageUnavailableError()
            val jsonish = match.groupValues[1]
            return try {
                JSONObject(jsonish)
            } catch (first: Exception) {
                // Values may be single-quoted; normalise then retry once.
                try {
                    JSONObject(jsonish.replace(Regex("(['\"])?([a-zA-Z0-9_]+)(['\"])?\\s*:"), "\"$2\":").replace('\'', '"'))
                } catch (second: Exception) {
                    throw ConnectorProtocolError("Contenu Start( illisible", second)
                }
            }
        }

        private fun md5(bytes: ByteArray): ByteArray =
            java.security.MessageDigest.getInstance("MD5").digest(bytes)

        private fun sha256(bytes: ByteArray): ByteArray =
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    data class UserResource(
        val id: String,
        val name: String,
        val className: String?,
        val establishmentName: String,
        val defaultPeriodName: String?,
        val periodNames: List<String>,
    )

    data class LoginOutcome(val refreshToken: String, val username: String)

    /** Mutable per-connection state. */
    class SessionInfo {
        var accountKind: Int = AccountKind.STUDENT
        var sessionId: Long = 0
        var order: Int = 0
        var encryptRequests = false
        var compressRequests = false
        var useFallbackRsa = true
    }

    private val info = SessionInfo()

    init {
        info.sessionId = sessionInfo.optLong("h", -1L)
        if (info.sessionId <= 0) throw PageUnavailableError()
        sessionInfo.optInt("a", -1).takeIf { it > 0 }?.let { info.accountKind = it }
        info.encryptRequests = sessionInfo.has("CrA") && sessionInfo.optBoolean("CrA")
        info.compressRequests = sessionInfo.has("CoA") && sessionInfo.optBoolean("CoA")
        info.useFallbackRsa = !(sessionInfo.has("MR") && sessionInfo.has("ER"))
    }

    /** AES IV temporaire du client ; l'IV réel utilisé ensuite est MD5(ivTemp). */
    private val ivTemp: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private val realIV: ByteArray = md5(ivTemp)
    private val initialKey: ByteArray = md5(ByteArray(0))

    /** Clé AES de session ; vaut la clé du challenge jusqu'à la fin du login. */
    private var sessionKey: ByteArray = initialKey

    /** Dernière clé de challenge calculée (utile pour déchiffrer `cle`). */
    private var lastChallengeKey: ByteArray = initialKey

    var userResource: UserResource? = null
        private set
    var periods: List<Period> = emptyList()
        private set
    var authorizedOnglets: Set<Int> = emptySet()
        private set

    private fun isLight() = serverVersion.atLeast(2025, 1, 3)

    // ------------------------------------------------------------------ crypto

    private fun aesEncryptHex(plain: ByteArray, key: ByteArray, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plain).toHexString()
    }

    private fun aesDecrypt(hex: String, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(hex.hexToByteArray())
    }

    private fun rsaEncrypt(data: ByteArray): String {
        val modulusHex = if (info.useFallbackRsa) FALLBACK_MODULUS else sessionInfo.getString("MR")
        val exponentHex = if (info.useFallbackRsa) FALLBACK_EXPONENT else sessionInfo.getString("ER")
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(
            java.security.spec.RSAPublicKeySpec(BigInteger(modulusHex, 16), BigInteger(exponentHex, 16)),
        )
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP)
    }

    // ------------------------------------------------------------- transport

    private fun deflateToHex(plain: String): String {
        val deflater = Deflater(Deflater.BEST_SPEED, true)
        deflater.setInput(plain.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val buffer = ByteArray(64 * 1024)
        var size = 0
        while (!deflater.finished()) {
            size += deflater.deflate(buffer, size, buffer.size - size)
            if (size == buffer.size) break
        }
        deflater.end()
        return buffer.copyOf(size).toHexString()
    }

    private fun inflateFromHex(hex: String): String {
        val inflater = Inflater(true)
        inflater.setInput(hex.hexToByteArray())
        val buffer = ByteArray(8 * 1024 * 1024)
        val size = try {
            var total = 0
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer, total, buffer.size - total)
                total += n
                if (n == 0 && inflater.needsInput()) break
            }
            total
        } finally {
            inflater.end()
        }
        return String(buffer, 0, size, Charsets.UTF_8)
    }

    private fun httpPostJson(url: String, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", PRONOTE_UA)
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return stream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Sends one ordered request. Order numbers go 1, 3, 5… (pronotepy: `+2`).
     * The first request is encrypted with a zero IV, as in the reference clients.
     */
    private fun post(requestName: String, data: JSONObject): JSONObject {
        if (info.order + 1 >= 22) throw SessionExpiredError()
        info.order += 2
        val firstOrder = info.order == 1
        val orderIV = if (firstOrder) ByteArray(16) else realIV
        val order = aesEncryptHex(info.order.toString().toByteArray(Charsets.UTF_8), sessionKey, orderIV)
        val url = "$baseUrl/appelfonction/${info.accountKind}/${info.sessionId}/$order"

        var secureData: Any = JSONObject().put("data", data)
        if (!isLight()) {
            var body = data.toString()
            if (info.compressRequests) body = deflateToHex(body)
            if (info.encryptRequests) {
                val plain = if (info.compressRequests) body.hexToByteArray() else body.toByteArray(Charsets.UTF_8)
                body = aesEncryptHex(plain, sessionKey, realIV)
            }
            secureData = body
        }

        val envelope = JSONObject()
            .put("session", info.sessionId)
            .put("no", order)
            .put("id", requestName)
            .put("dataSec", secureData)

        val raw = httpPostJson(url, envelope.toString())
        val outer = JSONObject(raw)
        outer.optJSONObject("Erreur")?.let { erreur ->
            val code = erreur.optInt("G", -1)
            val title = erreur.optString("Titre", "")
            when {
                code == 22 || title.contains("La page a expir") -> throw SessionExpiredError()
                code == 23 || raw.contains("Vous avez dépassé le nombre d'erreurs") -> throw RateLimitedError()
                code == 5 || raw.contains("Votre adresse IP") -> throw SuspendedIPError()
                else -> throw ServerSideError(title.ifEmpty { "code $code" })
            }
        }
        if (raw.contains("La page a expir")) throw SessionExpiredError()
        if (raw.contains("Votre adresse IP")) throw SuspendedIPError()
        if (raw.contains("Vous avez d")) throw RateLimitedError()
        if (raw.contains("La page dem") || raw.contains("Impossible d'a")) throw PageUnavailableError()
        if (raw.contains("s refus")) throw AccessDeniedError()

        return decodeResponse(outer)
    }

    private fun decodeResponse(outer: JSONObject): JSONObject {
        val dataSec = outer.get("dataSec")
        if (!isLight()) {
            var content: String
            var asHex: String
            if (info.encryptRequests) {
                val decrypted = aesDecrypt(dataSec as String, sessionKey, realIV)
                content = String(decrypted, Charsets.ISO_8859_1)
                asHex = decrypted.toHexString()
            } else {
                asHex = dataSec as String
                content = asHex
            }
            if (info.compressRequests) {
                val hex = if (info.encryptRequests) asHex else dataSec as String
                content = inflateFromHex(hex)
            } else if (info.encryptRequests) {
                // already plain JSON text in `content`
            }
            return JSONObject(content)
        }
        return when (dataSec) {
            is JSONObject -> dataSec
            is String -> JSONObject(dataSec)
            else -> throw ConnectorProtocolError("dataSec de type inattendu (${dataSec.javaClass.simpleName})")
        }
    }

    /** Reads `data` (2024+) or `donnees` (older) from a decoded response. */
    private fun JSONObject.contentData(): JSONObject =
        optJSONObject("data") ?: optJSONObject("donnees") ?: JSONObject()

    // ----------------------------------------------------------------- login

    /**
     * Full credential login. Returns the refresh token for later mobile-token
     * connections. Enforces security outcomes (BadCredentials / double auth).
     */
    fun loginWithCredentials(username: String, password: String, deviceUuid: String): LoginOutcome {
        // 1 — FonctionParametres with the RSA-encrypted (or raw) IV.
        val uuid = if (info.useFallbackRsa && !isLight()) rsaEncrypt(ivTemp) else Base64.encodeToString(ivTemp, Base64.NO_WRAP)
        post("FonctionParametres", JSONObject().put("Uuid", uuid).put("identifiantNav", JSONObject.NULL))

        // 2 — Identification.
        val identification = post(
            "Identification",
            JSONObject()
                .put("genreConnexion", 0)
                .put("genreEspace", info.accountKind)
                .put("identifiant", username)
                .put("pourENT", false)
                .put("enConnexionAuto", false)
                .put("demandeConnexionAuto", false)
                .put("enConnexionAppliMobile", false)
                .put("demandeConnexionAppliMobile", true)
                .put("demandeConnexionAppliMobileJeton", false)
                .put("uuidAppliMobile", deviceUuid)
                .put("loginTokenSAV", ""),
        ).contentData()
        val challenge = identification.optString("challenge")
        if (challenge.isEmpty()) throw ConnectorProtocolError("Challenge absent de la réponse Identification")

        // 3 — Challenge key: MD5(username + SHA256(alea + password)).
        var effUser = username
        var effPassword = password
        if (identification.optInt("modeCompLog", 0) == 1) effUser = effUser.lowercase()
        if (identification.optInt("modeCompMdp", 0) == 1) effPassword = effPassword.lowercase()
        val alea = identification.optString("alea", "")
        val hash = sha256((alea + effPassword).toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
        val challengeKey = md5((effUser + hash).toByteArray(Charsets.UTF_8))
        lastChallengeKey = challengeKey

        // 4 — Authentification with the re-encrypted challenge.
        val auth = post(
            "Authentification",
            JSONObject()
                .put("connexion", 0)
                .put("challenge", aesEncryptHex(challenge.toByteArray(Charsets.ISO_8859_1), challengeKey, realIV))
                .put("espace", info.accountKind),
        ).contentData()
        when (auth.optInt("Acces", 0)) {
            0 -> Unit
            1 -> throw BadCredentialsError()
            2, 3, 4, 5, 7, 8 -> throw AccessDeniedError()
            6, 10 -> throw AccountDisabledError()
            9 -> {
                val message = auth.optJSONObject("AccesMessage")?.optString("message").orEmpty()
                if (message.contains("Double authentification", ignoreCase = true) ||
                    auth.has("actionsDoubleAuth")
                ) {
                    throw SecurityRequiredError()
                }
                throw BadCredentialsError()
            }
            else -> throw BadCredentialsError()
        }
        if (auth.has("actionsDoubleAuth")) throw SecurityRequiredError()

        // 5 — Session AES key delivered inside the authentication response.
        val cle = auth.optString("cle")
        if (cle.isNotEmpty()) {
            sessionKey = md5(aesDecrypt(cle, challengeKey, realIV))
        }
        loadUserParameters()
        return LoginOutcome(refreshToken = auth.optString("jetonConnexionAppliMobile"), username = effUser)
    }

    /** Token login (mobile app flow, `appliMobile=1` cookie + reuse flags). */
    fun loginWithToken(username: String, token: String, deviceUuid: String): LoginOutcome {
        val uuid = if (info.useFallbackRsa && !isLight()) rsaEncrypt(ivTemp) else Base64.encodeToString(ivTemp, Base64.NO_WRAP)
        post("FonctionParametres", JSONObject().put("Uuid", uuid).put("identifiantNav", JSONObject.NULL))

        val identification = post(
            "Identification",
            JSONObject()
                .put("genreConnexion", 0)
                .put("genreEspace", info.accountKind)
                .put("identifiant", username)
                .put("pourENT", false)
                .put("enConnexionAuto", false)
                .put("demandeConnexionAuto", false)
                .put("enConnexionAppliMobile", true)
                .put("demandeConnexionAppliMobile", false)
                .put("demandeConnexionAppliMobileJeton", false)
                .put("uuidAppliMobile", deviceUuid)
                .put("loginTokenSAV", ""),
        ).contentData()
        val challenge = identification.optString("challenge")
        if (challenge.isEmpty()) throw ConnectorProtocolError("Challenge absent de la réponse Identification")

        var effUser = username
        if (identification.optInt("modeCompLog", 0) == 1) effUser = effUser.lowercase()
        val challengeKey = md5(effUser.toByteArray(Charsets.UTF_8) + sha256(token.toByteArray(Charsets.UTF_8)))
        lastChallengeKey = challengeKey

        val auth = post(
            "Authentification",
            JSONObject()
                .put("connexion", 0)
                .put("challenge", aesEncryptHex(challenge.toByteArray(Charsets.ISO_8859_1), challengeKey, realIV))
                .put("espace", info.accountKind),
        ).contentData()
        when (auth.optInt("Acces", 0)) {
            0 -> Unit
            1 -> throw BadCredentialsError()
            2, 3, 4, 5, 7, 8 -> throw AccessDeniedError()
            6, 10 -> throw AccountDisabledError()
            9 -> throw SecurityRequiredError()
            else -> throw BadCredentialsError()
        }
        val cle = auth.optString("cle")
        if (cle.isNotEmpty()) {
            sessionKey = md5(aesDecrypt(cle, challengeKey, realIV))
        }
        loadUserParameters()
        return LoginOutcome(refreshToken = auth.optString("jetonConnexionAppliMobile"), username = effUser)
    }

    // -------------------------------------------------------------- post-login

    private fun loadUserParameters() {
        val response = post("ParametresUtilisateur", JSONObject()).contentData()
        val ressource = response.optJSONObject("ressource") ?: throw ConnectorProtocolError("ParametresUtilisateur sans ressource")
        val periodsList = mutableListOf<Period>()
        var defaultPeriod: String? = null
        ressource.optJSONObject("listeOngletsPourPeriodes")?.optJSONArray("V")?.let { onglets ->
            for (i in 0 until onglets.length()) {
                val onglet = onglets.optJSONObject(i) ?: continue
                onglet.optJSONObject("listePeriodes")?.optJSONArray("V")?.let { periodes ->
                    for (j in 0 until periodes.length()) {
                        val periode = periodes.optJSONObject(j) ?: continue
                        val name = periode.optString("L")
                        if (name.isNotEmpty()) periodsList.add(Period(id = periode.optInt("N"), name = name))
                    }
                }
                onglet.optJSONObject("periodeParDefaut")?.optJSONObject("V")?.let { defaut ->
                    val name = defaut.optString("L")
                    if (name.isNotEmpty()) defaultPeriod = name
                }
            }
        }
        periods = periodsList.distinctBy { it.name }
        authorizedOnglets = buildSet {
            response.optJSONObject("autorisations")?.optJSONObject("listeOnglets")?.optJSONArray("V")?.let { onglets ->
                for (i in 0 until onglets.length()) {
                    onglets.optJSONObject(i)?.optInt("G")?.let { if (it != 0) add(it) }
                }
            }
        }
        userResource = UserResource(
            id = ressource.optString("N"),
            name = ressource.optString("L"),
            className = ressource.optJSONObject("classeDEleve")?.optString("L")?.takeIf { it.isNotEmpty() },
            establishmentName = ressource.optJSONObject("Etablissement")?.optJSONObject("V")?.optString("L").orEmpty(),
            defaultPeriodName = defaultPeriod,
            periodNames = periods.map { it.name },
        )
    }

    // ------------------------------------------------------------ snapshots

    data class Grade(
        val id: String,
        val subjectId: String,
        val subjectName: String,
        val title: String,
        val value: Double,
        val outOf: Double,
        val coefficient: Double,
        val date: String,
        val comment: String,
        val nonSignificant: Boolean,
    )

    data class SubjectAverage(val subjectId: String, val subjectName: String, val value: Double)
    data class Period(val id: Int, val name: String)

    private fun decodeGradeValue(value: JSONObject): Pair<Double, Boolean> {
        val raw = value.optString("V")
        return if (raw.contains("|")) {
            val kind = raw.split('|').getOrNull(1)?.toIntOrNull() ?: 0
            val points = raw.substringBefore('|').replace(',', '.').toDoubleOrNull() ?: 0.0
            val zeroed = kind == 6 || kind == 7 // AbsentZero / UnreturnedZero
            (if (zeroed) 0.0 else points) to (kind != 0)
        } else {
            (raw.replace(',', '.').toDoubleOrNull() ?: 0.0) to false
        }
    }

    /** Latest grades + per-subject averages for the student's default period. */
    fun latestGrades(periodName: String?): Pair<List<Grade>, List<SubjectAverage>> {
        val period = periods.firstOrNull { it.name == periodName }
            ?: periods.firstOrNull()
            ?: throw ConnectorProtocolError("Aucune période disponible sur ce compte")
        val response = post(
            "DernieresNotes",
            JSONObject().put("Periode", JSONObject().put("_T", 11).put("V", period.id)),
        ).contentData()

        val grades = mutableListOf<Grade>()
        response.optJSONObject("listeDevoirs")?.optJSONArray("V")?.let { list ->
            for (i in 0 until list.length()) {
                val devoir = list.optJSONObject(i) ?: continue
                val service = devoir.optJSONObject("service") ?: continue
                val notePair = devoir.optJSONObject("note")?.let { decodeGradeValue(it) } ?: (0.0 to false)
                val bareme = devoir.optJSONObject("bareme")?.optString("V")?.replace(',', '.')?.toDoubleOrNull() ?: 20.0
                grades.add(
                    Grade(
                        id = devoir.optString("N"),
                        subjectId = service.optString("N"),
                        subjectName = service.optString("L"),
                        title = devoir.optString("commentaire").ifEmpty { service.optString("L") },
                        value = notePair.first,
                        outOf = bareme,
                        coefficient = devoir.optDouble("coefficient", 1.0),
                        date = decodePronoteDate(devoir.optJSONObject("date")),
                        comment = devoir.optString("commentaire"),
                        nonSignificant = notePair.second,
                    ),
                )
            }
        }
        val averages = mutableListOf<SubjectAverage>()
        response.optJSONObject("listeServices")?.optJSONArray("V")?.let { list ->
            for (i in 0 until list.length()) {
                val service = list.optJSONObject(i) ?: continue
                val moyEleve = service.optJSONObject("moyEleve")?.optString("V") ?: continue
                val value = moyEleve.replace(',', '.').toDoubleOrNull() ?: continue
                averages.add(
                    SubjectAverage(
                        subjectId = service.optString("N"),
                        subjectName = service.optString("L"),
                        value = value,
                    ),
                )
            }
        }
        return grades to averages
    }

    private fun decodePronoteDate(value: JSONObject?): String {
        val raw = value?.optString("V").orEmpty() // "dd/MM/yyyy"
        val parts = raw.split('/')
        if (parts.size != 3) return ""
        return "%04d-%02d-%02d".format(parts[2].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0, parts[0].toIntOrNull() ?: 0)
    }
}

private fun List<Int>.atLeast(year: Int, month: Int, patch: Int): Boolean {
    if (isEmpty()) return false
    if (this[0] != year) return this[0] > year
    if (getOrNull(1) != month) return (getOrNull(1) ?: 0) > month
    return (getOrNull(2) ?: 0) >= patch
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToByteArray(): ByteArray {
    val cleaned = filter { !it.isWhitespace() }
    require(cleaned.length % 2 == 0) { "Chaîne hexadécimale de longueur impaire" }
    return ByteArray(cleaned.length / 2) { i ->
        ((Character.digit(cleaned[2 * i], 16) shl 4) + Character.digit(cleaned[2 * i + 1], 16)).toByte()
    }
}
