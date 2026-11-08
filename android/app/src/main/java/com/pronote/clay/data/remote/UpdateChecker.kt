package com.pronote.clay.data.remote

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ce que le site de distribution annonce de l'APK publié
 * (`GET /version.json`).
 */
data class UpdateInfo(
    val versionName: String?,
    val versionCode: Int,
    val size: Long?,
    val sha256: String?,
    val apkUrl: String,
)

/** Résultat d'une vérification de mise à jour. */
sealed interface UpdateStatus {
    /** Aucune vérification faite pour l'instant. */
    data object Idle : UpdateStatus

    data object Checking : UpdateStatus

    /** La version installée est la plus récente publiée. */
    data object UpToDate : UpdateStatus

    /**
     * Une version plus récente est publiée. [promptVisible] passe à faux quand
     * l'utilisateur a reporté : l'information reste consultable dans les
     * réglages.
     */
    data class Available(
        val info: UpdateInfo,
        val promptVisible: Boolean = true,
    ) : UpdateStatus

    /** Réseau ou manifeste indisponible : on ne sait pas, on ne propose rien. */
    data object Unavailable : UpdateStatus
}

/**
 * Vérifie, sur le site qui publie l'APK, si une version plus récente
 * d'Ardoise est disponible.
 *
 * Une seule requête HTTPS vers `version.json` : ni identifiant, ni donnée
 * scolaire, rien d'autre que la requête elle-même. Le [fetch] est injectable
 * pour les tests ; le défaut lit le manifeste en bloquant, donc l'appelant doit
 * être sur un fil d'entrée/sortie.
 */
class UpdateChecker(
    private val currentVersionCode: Int,
    private val siteUrl: String = SITE_URL,
    private val fetch: (String) -> String = ::fetchBody,
) {
    /** Compare la version publiée à la version installée. Ne lève jamais. */
    fun check(): UpdateStatus = try {
        val info = parse(fetch("$siteUrl$VERSION_PATH"), siteUrl)
        when {
            info == null -> UpdateStatus.Unavailable
            info.versionCode > currentVersionCode -> UpdateStatus.Available(info)
            else -> UpdateStatus.UpToDate
        }
    } catch (_: Exception) {
        UpdateStatus.Unavailable
    }

    companion object {
        /** Le site qui sert l'APK et son manifeste de version. */
        const val SITE_URL = "https://site-212736d17ebe48aea4f786a17c2d27ff.freebuff.page"

        const val VERSION_PATH = "/version.json"

        /**
         * Lit le manifeste. Retourne null quand il est absent, incomplet ou
         * illisible : mieux vaut ne rien dire qu'annoncer une mise à jour
         * fausse.
         */
        fun parse(body: String, siteUrl: String = SITE_URL): UpdateInfo? = try {
            val json = JSONObject(body)
            val versionCode = json.optInt("versionCode", -1)
            if (versionCode <= 0) {
                null
            } else {
                val path = json.optString("apkUrl").ifBlank { "/Ardoise.apk" }
                UpdateInfo(
                    versionName = json.optString("versionName").takeIf { it.isNotBlank() },
                    versionCode = versionCode,
                    size = json.optLong("size", -1).takeIf { it > 0 },
                    sha256 = json.optString("sha256").takeIf { it.isNotBlank() },
                    apkUrl = if (path.startsWith("http")) path else siteUrl.trimEnd('/') + path,
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Lecture simple du manifeste, avec des délais courts : la vérification ne doit jamais attendre. */
private fun fetchBody(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("accept", "application/json")
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            throw IOException("manifeste de version indisponible (HTTP $code)")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
