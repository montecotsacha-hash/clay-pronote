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

// ----------------------------------------------------------------- session

internal class PronoteSession private constructor(
    val baseUrl: String,
    val serverVersion: List<Int>,
    private val sessionInfo: JSONObject,
) {
    companion object {
        private const val PRONOTE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Mobile/15E148 PRONOTE Mobile APP Version/2.0.11"

        private const val BYDLG = "A6ABB224-12DD-4E31-AD3E-8A39A1C2C335"

        private const val HTTP_TIMEOUT_MS = 20_000

        /** Modulus RSA de repli du client web quand la page omet MR/ER. */
        private const val FALLBACK_MODULUS =
            "B99B77A3D72D3A29B4271FC7B7300E2F791EB8948174BE7B8024667E915446D4EEA0C2424B8D1EBF7E2DDFF94691C6E994E839225C627D140A8F1146D1B0B5F18A09BBD3D8F421CA1E3E4796B301EEBCCF80D81A32A1580121B8294433C38377083C5517D5921E8A078CDC019B15775292EFDA2C30251B1CCABE812386C893E5"
        private const val FALLBACK_EXPONENT =
            "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010001"

        /** Ouvre une session mobile pour l'URL et le type de compte donnés. */
        fun open(rawUrl: String, kind: Int, tokenFlow: Boolean): PronoteSession {
            val baseUrl = cleanUrl(rawUrl)
            val pageUrl = "$baseUrl/mobile.${AccountKind.path(kind)}.html" +
                "?fd=1&login=true&bydlg=$BYDLG"
            val html = httpGet(pageUrl, tokenFlow)
            val version = parseVersion(html) ?: throw PageUnavailableError()
            val start = parseStart(html)
            return PronoteSession(baseUrl, version, start).also { it.accountKind = kind }
        }

        /** Normalise une URL Pronote : hôte + chemin, sans page .html ni query. */
        fun cleanUrl(raw: String): String = try {
            val url = URL(raw)
            val path = url.path.orEmpty().split('/').filter { it.isNotEmpty() }.toMutableList()
            if (path.isNotEmpty() && path.last().contains(".html")) path.removeAt(path.lastIndex)
            val base = "${url.protocol}://${url.host}" +
                (if (url.port != -1 && url.port != url.defaultPort) ":${url.port}" else "")
            if (path.isEmpty()) base else "$base/${path.joinToString("/")}"
        } catch (error: Exception) {
            throw (PageUnavailableError().initCause(error) as PageUnavailableError)
        }

        private fun httpGet(url: String, tokenFlow: Boolean): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", PRONOTE_UA)
            if (tokenFlow) connection.setRequestProperty("Cookie", "appliMobile=1")
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw PageUnavailableError()
                return connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } finally {
                connection.disconnect()
            }
        }

        /** Extrait `>PRONOTE 20xx.y.z` de la page. */
        private fun parseVersion(html: String): List<Int>? {
            val match = Regex(">PRONOTE (\\d+\\.\\d+\\.\\d+)").find(html) ?: return null
            return match.groupValues[1].split('.').map { it.toInt() }
        }

        /**
         * Extrait les arguments de session de `Start ( {...} );}`.
         * Parseur à accolades/chaînes : tolère espaces, retours ligne et la
         * parenthèse finale — le marqueur `)}catch` de Pawnote 1.0.0 n'existe
         * plus sur les pages 2026.
         */
        private fun parseStart(html: String): JSONObject {
            if (html.contains("Votre adresse IP est provisoirement suspendue")) throw SuspendedIPError()
            if (html.contains("Le site est momentanément indisponible")) throw BusyPageError()
            if (html.contains("Le site n'est pas disponible")) throw PageUnavailableError()
            val idx = html.indexOf("Start")
            if (idx < 0) throw PageUnavailableError()
            val begin = html.indexOf('{', idx)
            if (begin < 0) throw PageUnavailableError()
            var depth = 0
            var inString = false
            var quote = ' '
            var end = -1
            var i = begin
            while (i < html.length && end < 0) {
                val c = html[i]
                if (inString) {
                    if (c == '\\') {
                        i += 2
                        continue
                    }
                    if (c == quote) inString = false
                } else {
                    when {
                        c == '"' || c == '\'' -> {
                            inString = true
                            quote = c
                        }
                        c == '{' -> depth += 1
                        c == '}' -> {
                            depth -= 1
                            if (depth == 0) end = i
                        }
                    }
                }
                i += 1
            }
            if (end < 0) throw PageUnavailableError()
            val jsonish = html.substring(begin, end + 1)
                .replace(Regex("(['\"])?([a-zA-Z0-9_]+)(['\"])?\\s*:"), "\"$2\":")
                .replace('\'', '"')
            return try {
                JSONObject(jsonish)
            } catch (error: Exception) {
                throw ConnectorProtocolError("Contenu Start( illisible", error)
            }
        }

        private fun md5(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("MD5").digest(bytes)

        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02X".format(it) }
    }

    data class UserResource(
        val id: String,
        val name: String,
        val className: String?,
        val establishmentName: String,
    )

    data class LoginOutcome(val refreshToken: String, val username: String)

    data class Period(val id: String, val name: String)

    var accountKind: Int = AccountKind.STUDENT
    var sessionId: Long = 0L
    var order: Int = -1
    var encryptRequests = false
    var compressRequests = false
    var rsaFromConstants = false

    /** Premier lundi de l'année scolaire (`General.PremierLundi`), si fourni. */
    private var firstMonday: java.time.LocalDate? = null

    /** `ressource` de ParametresUtilisateur, réinjectée dans PageEmploiDuTemps. */
    private var timetableResource: Any? = null

    /**
     * Grille horaire de l'établissement (`General.ListeHeuresFin`), utilisée
     * uniquement quand un cours arrive sans heure de fin.
     */
    private var endHours: List<Pair<Int, Int>>? = null

    /** IV temporaire du client ; l'IV réel utilisé ensuite est MD5(ivTemp). */
    private val ivTemp: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private val realIV: ByteArray = md5(ivTemp)
    private val initialKey: ByteArray = md5(ByteArray(0))

    /** Clé AES de session ; vaut la clé initiale jusqu'à la fin du login. */
    private var sessionKey: ByteArray = initialKey

    var userResource: UserResource? = null
        private set
    var periods: List<Period> = emptyList()
        private set
    var defaultPeriod: Period? = null
        private set

    init {
        sessionId = sessionInfo.optLong("h", -1L)
        if (sessionId <= 0) throw PageUnavailableError()
        sessionInfo.optInt("a", -1).takeIf { it > 0 }?.let { accountKind = it }
        val hasRsa = sessionInfo.has("MR") && sessionInfo.has("ER")
        rsaFromConstants = hasRsa
        val version2025 = serverVersion.atLeast(2025, 1, 3)
        encryptRequests = if (version2025) {
            sessionInfo.has("CrA") && sessionInfo.optBoolean("CrA")
        } else {
            sessionInfo.optBoolean("sCrA", false)
        }
        compressRequests = if (version2025) {
            sessionInfo.has("CoA") && sessionInfo.optBoolean("CoA")
        } else {
            sessionInfo.optBoolean("sCoA", false)
        }
    }

    /** AES-CBC PKCS5 → hex. */
    private fun aesEncryptHex(plain: ByteArray, key: ByteArray, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plain).joinToString("") { "%02x".format(it) }
    }

    /** hex → AES-CBC PKCS5 déchiffré. */
    private fun aesDecrypt(hex: String, key: ByteArray, iv: ByteArray): ByteArray {
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(bytes)
    }

    private fun rsaEncrypt(data: ByteArray): String {
        val modulusHex = if (rsaFromConstants) sessionInfo.getString("MR") else FALLBACK_MODULUS
        val exponentHex = if (rsaFromConstants) sessionInfo.getString("ER") else FALLBACK_EXPONENT
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(
            java.security.spec.RSAPublicKeySpec(BigInteger(modulusHex, 16), BigInteger(exponentHex, 16)),
        )
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP)
    }

    private fun deflateToHex(plain: String): String {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(plain.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            output.write(buffer, 0, n)
        }
        deflater.end()
        return output.toByteArray().joinToString("") { "%02x".format(it) }
    }

    private fun inflateFromHex(hex: String): String {
        return String(inflateBytes(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()), Charsets.UTF_8)
    }

    private fun inflateBytes(raw: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(raw)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && inflater.needsInput()) break
                output.write(buffer, 0, n)
            }
        } finally {
            inflater.end()
        }
        return output.toByteArray()
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
     * Envoie une requête ordonnée. Numéros d'ordre de 2 en 2 (1, 3, 5…) ;
     * l'ordre 1 est chiffré avec un IV zéro.
     */
    private fun post(requestName: String, data: JSONObject): JSONObject {
        order += 2
        val orderIV = if (order == 1) ByteArray(16) else realIV
        val orderHex = aesEncryptHex(order.toString().toByteArray(Charsets.UTF_8), sessionKey, orderIV)
        val url = "$baseUrl/appelfonction/$accountKind/$sessionId/$orderHex"

        val modern = version2025OrLater()
        // Enveloppe ≥2025.1.3 : {session,no,id,dataSec} ; plus ancien :
        // {session,numeroOrdre,nom,donneesSec}.
        val dataSecBody: Any = if (modern) {
            data
        } else {
            var body = data.toString()
            if (compressRequests) body = deflateToHex(body)
            if (encryptRequests) {
                val plain = if (compressRequests) {
                    body.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                } else {
                    body.toByteArray(Charsets.UTF_8)
                }
                body = aesEncryptHex(plain, sessionKey, realIV)
            }
            body
        }

        val envelope = if (modern) {
            JSONObject()
                .put("session", sessionId)
                .put("no", orderHex)
                .put("id", requestName)
                .put("dataSec", dataSecBody)
        } else {
            JSONObject()
                .put("session", sessionId)
                .put("numeroOrdre", orderHex)
                .put("nom", requestName)
                .put("donneesSec", dataSecBody)
        }

        val raw = httpPostJson(url, envelope.toString())
        if (raw.contains("La page a expir")) throw SessionExpiredError()
        if (raw.contains("Votre adresse IP")) throw SuspendedIPError()
        if (raw.contains("Vous avez d")) throw RateLimitedError()
        if (raw.contains("La page dem") || raw.contains("Impossible d'a")) throw PageUnavailableError()
        if (raw.contains("s refus")) throw AccessDeniedError()
        val outer = try {
            JSONObject(raw)
        } catch (error: Exception) {
            throw ConnectorProtocolError("Réponse non-JSON : ${raw.take(120)}", error)
        }
        outer.optJSONObject("Erreur")?.let { erreur ->
            throw ServerSideError(erreur.optString("Titre").ifEmpty { "code ${erreur.optInt("G")}" })
        }
        return decodeResponse(outer)
    }

    private fun decodeResponse(outer: JSONObject): JSONObject {
        val dataSec = outer.opt("dataSec") ?: outer.opt("donneesSec")
        if (encryptRequests && dataSec is String) {
            val decrypted = aesDecrypt(dataSec, sessionKey, realIV)
            val content = if (compressRequests) {
                inflateBytes(decrypted).toString(Charsets.UTF_8)
            } else {
                String(decrypted, Charsets.UTF_8)
            }
            return JSONObject(content)
        }
        if (!encryptRequests && dataSec is String) {
            val content = if (compressRequests) inflateFromHex(dataSec) else dataSec
            return JSONObject(content)
        }
        return when (dataSec) {
            is JSONObject -> dataSec
            else -> JSONObject()
        }
    }

    /** Lit `data` (2024+) ou `donnees` (plus ancien) d'une réponse décodée. */
    private fun JSONObject.contentData(): JSONObject =
        optJSONObject("data") ?: optJSONObject("donnees") ?: JSONObject()

    private fun version2025OrLater(): Boolean = serverVersion.atLeast(2025, 1, 3)

    fun loginWithCredentials(username: String, password: String, deviceUuid: String): LoginOutcome {
        // 1 — FonctionParametres avec l'IV (chiffré RSA ou brut base64).
        val uuid = if (rsaFromConstants) {
            rsaEncrypt(ivTemp)
        } else {
            Base64.encodeToString(ivTemp, Base64.NO_WRAP)
        }
        val fpPayload = JSONObject().put("Uuid", uuid).put("identifiantNav", JSONObject.NULL)
        val functionParams = post(
            "FonctionParametres",
            JSONObject().put("data", fpPayload).put("donnees", fpPayload),
        ).contentData()

        // 2 — Identification (flux appli mobile, login + mdp).
        val identification = post(
            "Identification",
            JSONObject()
                .put("data", identificationPayload(username, deviceUuid)),
        ).contentData()
        val challenge = identification.optString("challenge")
        if (challenge.isEmpty()) {
            throw ConnectorProtocolError("Challenge absent de la réponse Identification")
        }

        // 3 — Clé du challenge : MD5(username + SHA256(alea + password) hex MAJ).
        var effUser = username
        var effPassword = password
        if (identification.optInt("modeCompLog", 0) == 1) effUser = effUser.lowercase()
        if (identification.optInt("modeCompMdp", 0) == 1) effPassword = effPassword.lowercase()
        val alea = identification.optString("alea", "")
        val challengeKey = md5((effUser + sha256Hex((alea + effPassword).toByteArray(Charsets.UTF_8))).toByteArray(Charsets.UTF_8))

        // 4 — Authentification : challenge 2026 en clair, chiffré tel quel.
        val auth = post(
            "Authentification",
            JSONObject()
                .put(
                    "data",
                    JSONObject()
                        .put("connexion", 0)
                        .put("challenge", aesEncryptHex(challenge.toByteArray(Charsets.UTF_8), challengeKey, realIV))
                        .put("espace", accountKind),
                ),
        ).contentData()
        when (auth.optInt("Acces", 0)) {
            0 -> Unit
            1 -> throw BadCredentialsError()
            2, 3, 4, 5, 7, 8 -> throw AccessDeniedError()
            6, 10 -> throw AccountDisabledError()
            9 -> throw SecurityRequiredError()
            else -> throw BadCredentialsError()
        }
        if (auth.has("actionsDoubleAuth")) throw SecurityRequiredError()

        // 5 — Clé de session : déchiffrer `cle`, octets bruts, MD5.
        applySessionKey(auth.optString("cle"), challengeKey)
        loadUserParameters(functionParams)
        return LoginOutcome(
            refreshToken = auth.optString("jetonConnexionAppliMobile"),
            username = effUser,
        )
    }

    fun loginWithToken(username: String, token: String, deviceUuid: String): LoginOutcome {
        val uuid = if (rsaFromConstants) {
            rsaEncrypt(ivTemp)
        } else {
            Base64.encodeToString(ivTemp, Base64.NO_WRAP)
        }
        val fpPayload = JSONObject().put("Uuid", uuid).put("identifiantNav", JSONObject.NULL)
        val functionParams = post(
            "FonctionParametres",
            JSONObject().put("data", fpPayload).put("donnees", fpPayload),
        ).contentData()

        val identification = post(
            "Identification",
            JSONObject()
                .put("data", identificationPayload(username, deviceUuid, tokenFlow = true)),
        ).contentData()
        val challenge = identification.optString("challenge")
        if (challenge.isEmpty()) {
            throw ConnectorProtocolError("Challenge absent de la réponse Identification")
        }

        var effUser = username
        if (identification.optInt("modeCompLog", 0) == 1) effUser = effUser.lowercase()
        val alea = identification.optString("alea", "")
        val challengeKey = md5(
            (effUser + sha256Hex((alea + token).toByteArray(Charsets.UTF_8))).toByteArray(Charsets.UTF_8),
        )

        val auth = post(
            "Authentification",
            JSONObject()
                .put(
                    "data",
                    JSONObject()
                        .put("connexion", 0)
                        .put("challenge", aesEncryptHex(challenge.toByteArray(Charsets.UTF_8), challengeKey, realIV))
                        .put("espace", accountKind),
                ),
        ).contentData()
        when (auth.optInt("Acces", 0)) {
            0 -> Unit
            1 -> throw BadCredentialsError()
            2, 3, 4, 5, 7, 8 -> throw AccessDeniedError()
            6, 10 -> throw AccountDisabledError()
            9 -> throw SecurityRequiredError()
            else -> throw BadCredentialsError()
        }
        if (auth.has("actionsDoubleAuth")) throw SecurityRequiredError()

        applySessionKey(auth.optString("cle"), challengeKey)
        loadUserParameters(functionParams)
        return LoginOutcome(
            refreshToken = auth.optString("jetonConnexionAppliMobile"),
            username = effUser,
        )
    }

    private fun identificationPayload(
        username: String,
        deviceUuid: String,
        tokenFlow: Boolean = false,
    ): JSONObject {
        val payload = JSONObject()
            .put("genreConnexion", 0)
            .put("genreEspace", accountKind)
            .put("identifiant", username)
            .put("pourENT", false)
            .put("enConnexionAuto", false)
            .put("demandeConnexionAuto", false)
            .put("enConnexionAppliMobile", tokenFlow)
            .put("demandeConnexionAppliMobile", !tokenFlow)
            .put("demandeConnexionAppliMobileJeton", false)
            .put("uuidAppliMobile", deviceUuid)
            .put("loginTokenSAV", "")
            .put("informationsAppareil", JSONObject().put("modele", "iPhone18,3").put("platforme", "iOS"))
        return payload
    }

    /**
     * Décode `cle` : déchiffrer avec la clé du challenge, puis les codes
     * virgulés SONT les octets bruts de la clé ; la clé de session = MD5.
     */
    private fun applySessionKey(cle: String, challengeKey: ByteArray) {
        if (cle.isEmpty()) return
        val decrypted = String(aesDecrypt(cle, challengeKey, realIV), Charsets.ISO_8859_1)
        sessionKey = if (Regex("^\\d+(,\\d+)*$").matches(decrypted)) {
            val raw = decrypted.split(',').map { it.trim().toInt().toByte() }.toByteArray()
            md5(raw)
        } else {
            md5(decrypted.toByteArray(Charsets.UTF_8))
        }
    }

    private fun loadUserParameters(functionParams: JSONObject = JSONObject()) {
        // Le premier lundi de l'année scolaire est l'ancre du numéro de
        // semaine attendu par l'emploi du temps (General.PremierLundi), et la
        // grille horaire sert de repli quand un cours n'a pas d'heure de fin.
        functionParams.optJSONObject("General")?.let { general ->
            general.optJSONObject("PremierLundi")
                ?.optString("V")
                ?.takeIf { it.isNotBlank() }
                ?.let { firstMonday = parseFrenchDate(it) }
            endHours = readHourGrid(general.opt("ListeHeuresFin"))
        }

        val response = post("ParametresUtilisateur", JSONObject()).contentData()
        // La ressource de l'élève sert telle quelle de filtre à PageEmploiDuTemps.
        timetableResource = response.opt("ressource")
        val ressource = response.optJSONObject("ressource")
            ?: throw ConnectorProtocolError("ParametresUtilisateur sans ressource")

        val periodList = mutableListOf<Period>()
        var defaut: Period? = null
        ressource.optJSONObject("listeOngletsPourPeriodes")?.optJSONArray("V")?.let { onglets ->
            for (i in 0 until onglets.length()) {
                val onglet = onglets.optJSONObject(i) ?: continue
                onglet.optJSONObject("listePeriodes")?.optJSONArray("V")?.let { periodes ->
                    for (j in 0 until periodes.length()) {
                        val periode = periodes.optJSONObject(j) ?: continue
                        val id = periode.optString("N")
                        val name = periode.optString("L")
                        if (id.isNotEmpty() && name.isNotEmpty()) periodList.add(Period(id, name))
                    }
                }
                onglet.optJSONObject("periodeParDefaut")?.optJSONObject("V")?.let { defautObj ->
                    val id = defautObj.optString("N")
                    val name = defautObj.optString("L")
                    if (id.isNotEmpty() && name.isNotEmpty()) defaut = Period(id, name)
                }
            }
        }
        periods = periodList.distinctBy { it.name }
        defaultPeriod = defaut?.takeIf { def -> periods.any { it.id == def.id } }
            ?: periods.firstOrNull()

        val etablissement = ressource.optJSONObject("Etablissement")?.optJSONObject("V")?.optString("L").orEmpty()
        userResource = UserResource(
            id = ressource.optString("N"),
            name = ressource.optString("L"),
            className = ressource.optJSONObject("classeDEleve")?.optString("L")?.takeIf { it.isNotEmpty() },
            establishmentName = etablissement,
        )
    }

    /** Dernières notes + moyennes par matière pour la période par défaut. */
    fun readSnapshot(): PronoteRawPayload {
        val period = defaultPeriod
            ?: throw ConnectorProtocolError("Aucune période disponible sur ce compte")
        val response = post(
            "DernieresNotes",
            JSONObject()
                .put("Signature", JSONObject().put("onglet", 198))
                .put("data", JSONObject().put("Periode", JSONObject().put("N", period.id).put("L", period.name))),
        ).contentData()

        val subjects = LinkedHashMap<String, PronoteSubjectDto>()
        val grades = mutableListOf<PronoteGradeDto>()
        response.optJSONObject("listeDevoirs")?.optJSONArray("V")?.let { list ->
            for (i in 0 until list.length()) {
                val devoir = list.optJSONObject(i) ?: continue
                val service = devoir.optJSONObject("service")?.optJSONObject("V") ?: continue
                val subjectId = service.optString("N").ifEmpty { service.optString("L") }
                val subjectName = service.optString("L")
                subjects.getOrPut(subjectId) {
                    PronoteSubjectDto(
                        id = subjectId,
                        nom = subjectName,
                        nomCourt = subjectName.take(3).uppercase(),
                    )
                }
                val notePair = decodeGradeValue(devoir.optJSONObject("note")?.optString("V").orEmpty())
                val bareme = devoir.optJSONObject("bareme")?.optString("V")
                    ?.replace(',', '.')?.toDoubleOrNull() ?: 20.0
                grades.add(
                    PronoteGradeDto(
                        id = devoir.optString("N"),
                        matiere = subjectId,
                        intitule = devoir.optString("commentaire").ifEmpty { subjectName },
                        note = notePair.first,
                        bareme = bareme,
                        coefficient = devoir.optDouble("coefficient", 1.0),
                        date = decodePronoteDate(devoir.optJSONObject("date")?.optString("V").orEmpty()),
                        commentaire = devoir.optString("commentaire").takeIf { it.isNotEmpty() },
                        nonSignificatif = notePair.second,
                    ),
                )
            }
        }

        val averages = mutableListOf<PronoteSubjectAverageDto>()
        response.optJSONObject("listeServices")?.optJSONArray("V")?.let { list ->
            for (i in 0 until list.length()) {
                val service = list.optJSONObject(i) ?: continue
                val moyEleve = service.optJSONObject("moyEleve")?.optString("V").orEmpty()
                val value = moyEleve.replace(',', '.').toDoubleOrNull() ?: continue
                val matiere = service.optJSONObject("Matiere")?.optJSONObject("V")
                val subjectId = matiere?.optString("N")?.ifEmpty { null }
                    ?: service.optString("N").ifEmpty { matiere?.optString("L") ?: service.optString("L") }
                val subjectName = matiere?.optString("L") ?: service.optString("L")
                subjects.getOrPut(subjectId) {
                    PronoteSubjectDto(
                        id = subjectId,
                        nom = subjectName,
                        nomCourt = subjectName.take(3).uppercase(),
                    )
                }
                averages.add(PronoteSubjectAverageDto(matiere = subjectId, moyenne = value, periode = period.name))
            }
        }

        val generalAverage = response.optJSONObject("moyGenerale")?.optString("V")
            ?.replace(',', '.')?.toDoubleOrNull()
        // Pronote expose "NOM Prénom" : le prénom est après le dernier espace.
        val fullName = userResource?.name.orEmpty().trim()
        val firstName = if (fullName.contains(' ')) fullName.substringAfterLast(' ') else ""
        val lastName = if (fullName.contains(' ')) fullName.substringBeforeLast(' ') else fullName

        return PronoteRawPayload(
            student = PronoteStudentDto(
                id = userResource?.id,
                prenom = firstName,
                nom = lastName,
                classe = userResource?.className,
                etablissement = userResource?.establishmentName,
            ),
            subjects = subjects.values.toList(),
            grades = grades,
            subjectAverages = averages,
            generalAverage = generalAverage,
            period = period.name,
        )
    }

    /**
     * Emploi du temps d'une semaine — requête `PageEmploiDuTemps` (onglet 16).
     *
     * C'est LA requête que Pronote attend pour l'onglet emploi du temps : elle
     * ne prend pas une date mais un NUMÉRO DE SEMAINE (1 + nombre de semaines
     * depuis le premier lundi de l'année scolaire, `General.PremierLundi`) et
     * la ressource de l'élève remonte de `ParametresUtilisateur`. L'ancienne
     * requête `PageCalendrier` avec un faux `Periode._oeuf` n'existe pas côté
     * serveur : elle se faisait refuser, d'où le message « impossible de se
     * connecter » à l'ouverture de l'emploi du temps.
     *
     * Les cours arrivent dans `ListeCours` — tableau direct, ou enveloppé dans
     * `V` selon le serveur — avec des dates tantôt en tableau
     * [annee, mois, jour, heure, minutes], tantôt en chaîne : les deux formes
     * sont acceptées ici.
     *
     * Les clés de chaque cours sont celles du protocole mobile : `DateDuCours`,
     * `DateDuCoursFin`, `estAnnule`, `memo`, et surtout `ListeContenus.V`, où
     * matières (G=16), professeurs (G=3), salles (G=17) et groupes (G=2) sont
     * mélangés avec un code de type et un libellé `L`.
     */
    fun readTimetable(weekStartIso: String): List<PronoteLessonDto> {
        val weekNumber = weekNumberFor(weekStartIso)
        val payload = JSONObject()
            .put("numeroSemaine", weekNumber)
            .put("NumeroSemaine", weekNumber)
            .put("avecAbsencesEleve", false)
            .put("avecConseilDeClasse", true)
            .put("estEDTPermanence", false)
            .put("avecAbsencesRessource", true)
            .put("avecDisponibilites", true)
            .put("avecInfosPrefsGrille", true)
        // Certains serveurs attendent l'identifiant, d'autres l'objet ressource
        // complet : on renvoie tel quel ce que ParametresUtilisateur a donné,
        // sous les deux orthographes utilisées par les clients Pronote.
        timetableResource?.let { resource ->
            payload.put("ressource", resource)
            payload.put("Ressource", resource)
        }

        val response = post(
            "PageEmploiDuTemps",
            JSONObject()
                .put("Signature", JSONObject().put("onglet", 16))
                .put("data", payload)
                .put("donnees", payload),
        ).contentData()

        return parseTimetableLessons(response, endHours)
    }

    /**
     * Numéro de semaine Pronote de la semaine contenant [weekStartIso] :
     * 1 + le nombre de semaines depuis le premier lundi de l'année scolaire.
     */
    private fun weekNumberFor(weekStartIso: String): Int {
        val date = try {
            java.time.LocalDate.parse(weekStartIso)
        } catch (_: Exception) {
            return 1
        }
        val anchor = firstMonday ?: defaultSchoolYearMonday(date)
        val weeks = java.time.temporal.ChronoUnit.WEEKS.between(anchor, date)
        return (weeks + 1).toInt().coerceAtLeast(1)
    }

    /** Repli quand `General.PremierLundi` manque : le lundi de la rentrée. */
    private fun defaultSchoolYearMonday(date: java.time.LocalDate): java.time.LocalDate {
        val september = if (date.monthValue >= 8) {
            java.time.LocalDate.of(date.year, 9, 1)
        } else {
            java.time.LocalDate.of(date.year - 1, 9, 1)
        }
        return september.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
    }

    /** `dd/MM/yyyy` -> LocalDate, sans exception sur une valeur inattendue. */
    private fun parseFrenchDate(raw: String?): java.time.LocalDate? {
        val parts = raw.orEmpty().trim().split('/')
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        return try {
            java.time.LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Grille horaire `General.ListeHeuresFin.V` -> liste d'heures décodées.
     * Chaque entrée est un `{ "L": "08:00" }` ; une chaîne nue est acceptée.
     */
    private fun readHourGrid(raw: Any?): List<Pair<Int, Int>>? {
        val array = when (raw) {
            is org.json.JSONArray -> raw
            is org.json.JSONObject -> raw.optJSONArray("V")
            else -> null
        } ?: return null
        val hours = (0 until array.length()).mapNotNull { i ->
            val label = when (val item = array.opt(i)) {
                is String -> item
                is org.json.JSONObject -> item.optString("L")
                else -> null
            }?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val parts = label.split(':')
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            hour to minute
        }
        return hours.takeIf { it.size >= 2 }
    }

    private fun decodeGradeValue(raw: String): Pair<Double, Boolean> {
        if (raw.contains('|')) {
            val points = raw.substringBefore('|').replace(',', '.').toDoubleOrNull() ?: 0.0
            val kind = raw.substringAfter('|', "0").toIntOrNull() ?: 0
            val zeroed = kind == 6 || kind == 7
            return (if (zeroed) 0.0 else points) to (kind != 0)
        }
        return (raw.replace(',', '.').toDoubleOrNull() ?: 0.0) to false
    }

    private fun decodePronoteDate(raw: String): String? {
        val parts = raw.split('/')
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        return "%04d-%02d-%02d".format(year, month, day)
    }
}

