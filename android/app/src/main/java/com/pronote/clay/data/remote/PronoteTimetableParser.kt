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

internal fun List<Int>.atLeast(year: Int, month: Int, patch: Int): Boolean {
    if (isEmpty()) return false
    if (this[0] != year) return this[0] > year
    if (getOrNull(1) != month) return (getOrNull(1) ?: 0) > month
    return (getOrNull(2) ?: 0) >= patch
}

// ------------------------------------------- analyse de l'emploi du temps

/**
 * Analyse le `ListeCours` d'une réponse `PageEmploiDuTemps`.
 *
 * Extrait du client pour être testable sur la JVM avec une charge utile Pronote
 * réaliste : c'est ici que se joue la correspondance avec les clés réelles du
 * protocole mobile — `DateDuCours` / `DateDuCoursFin` enveloppés dans `V`,
 * `estAnnule`, `memo`, et surtout `ListeContenus.V`, où la matière (code `G`
 * 16), les professeurs (3), les salles (17) et les groupes (2) sont mélangés.
 *
 * Les replis (`Matiere`, `ListeProfesseurs`, `ListeSalles`, `ListeGroupes`) ne
 * servent qu'aux serveurs qui exposent encore ces clés dédiées.
 */
internal fun parseTimetableLessons(
    response: JSONObject,
    hourGrid: List<Pair<Int, Int>>? = null,
): List<PronoteLessonDto> {
    val list = response.optJSONArray("ListeCours")
        ?: response.optJSONObject("ListeCours")?.optJSONArray("V")
        ?: return emptyList()

    val lessons = mutableListOf<PronoteLessonDto>()
    for (i in 0 until list.length()) {
        val cours = list.optJSONObject(i) ?: continue
        val start = decodePronoteDateTime(cours.opt("DateDuCours")) ?: continue
        val end = decodePronoteDateTime(cours.opt("DateDuCoursFin"))
            ?: endFromGrid(cours, start, hourGrid)
            ?: start

        var matiereId: String? = null
        var matiereLabel: String? = null
        val professeurs = mutableListOf<String>()
        val salles = mutableListOf<String>()
        val groupes = mutableListOf<String>()
        listedObjects(cours, "ListeContenus").forEach { item ->
            val label = item.optString("L").takeIf { it.isNotBlank() }
            when (item.optInt("G", -1)) {
                16 -> {
                    matiereId = item.optString("N").takeIf { it.isNotBlank() }
                    matiereLabel = label
                }

                3 -> label?.let(professeurs::add)
                17 -> label?.let(salles::add)
                2 -> label?.let(groupes::add)
            }
        }
        if (matiereLabel == null) {
            val matiere = cours.optJSONObject("Matiere")
            matiereLabel = (matiere?.optJSONObject("V") ?: matiere)
                ?.optString("L")?.takeIf { it.isNotBlank() }
            if (matiereId == null) {
                matiereId = cours.optString("demandeInclusion").takeIf { it.isNotBlank() }
            }
        }
        if (professeurs.isEmpty()) firstLabel(cours, "ListeProfesseurs")?.let(professeurs::add)
        if (salles.isEmpty()) firstLabel(cours, "ListeSalles")?.let(salles::add)
        if (groupes.isEmpty()) firstLabel(cours, "ListeGroupes")?.let(groupes::add)

        // `estAnnule` est le drapeau officiel ; `Statut` porte le mot « Annulé »
        // sur les versions qui n'envoient pas le booléen.
        val statut = cours.optString("Statut").takeIf { it.isNotBlank() }
        val cancelled = cours.optBoolean("estAnnule", false) ||
            statut?.contains("annul", ignoreCase = true) == true

        val memoParts = mutableListOf<String>()
        cours.optString("memo").takeIf { it.isNotBlank() }?.let(memoParts::add)
        if (cours.optBoolean("estRetenue", false)) memoParts.add("Retenue")
        if (statut != null && !cancelled && !statut.equals("Normal", ignoreCase = true)) {
            memoParts.add(statut)
        }

        lessons.add(
            PronoteLessonDto(
                matiere = matiereId,
                nomMatiere = matiereLabel,
                professeur = professeurs.joinToString(", ").takeIf { it.isNotBlank() },
                salle = salles.joinToString(", ").takeIf { it.isNotBlank() },
                groupe = groupes.joinToString(", ").takeIf { it.isNotBlank() },
                debut = start,
                fin = end,
                annule = cancelled,
                memo = memoParts.joinToString(" — ").takeIf { it.isNotBlank() },
            ),
        )
    }
    return lessons.sortedBy { it.debut.orEmpty() }
}

/**
 * Lit une liste Pronote quelle que soit son enveloppe : tableau direct
 * (`"ListeContenus": [ … ]`) ou objet `{ "V": [ … ] }` selon le serveur.
 */
private fun listedObjects(parent: JSONObject, key: String): List<JSONObject> {
    val array = parent.optJSONArray(key)
        ?: parent.optJSONObject(key)?.optJSONArray("V")
        ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

/** Premier libellé non vide de `ListeSalles` / `ListeProfesseurs` / … */
private fun firstLabel(cours: JSONObject, key: String): String? {
    val array = cours.optJSONArray(key)
        ?: cours.optJSONObject(key)?.optJSONArray("V")
        ?: return null
    for (i in 0 until array.length()) {
        val label = array.optJSONObject(i)?.optString("L")
        if (!label.isNullOrBlank()) return label
    }
    return null
}

/**
 * Repli quand le serveur omet `DateDuCoursFin` : Pronote donne la « place » du
 * créneau dans la semaine et sa durée en créneaux ; la grille horaire de
 * l'établissement donne l'heure de fin. Même calcul que les clients libres :
 * `place % (n - 1) + duree - 1`.
 */
private fun endFromGrid(
    cours: JSONObject,
    startIso: String,
    grid: List<Pair<Int, Int>>?,
): String? {
    if (grid == null || grid.size < 2) return null
    val place = cours.optInt("place", 0)
    val duree = cours.optInt("duree", 0)
    if (place <= 0 || duree <= 0) return null
    val end = grid.getOrNull((place % (grid.size - 1)) + duree - 1) ?: return null
    val start = try {
        java.time.LocalDateTime.parse(startIso)
    } catch (_: Exception) {
        return null
    }
    return start.withHour(end.first).withMinute(end.second).withSecond(0).toString()
}

/**
 * Date Pronote -> ISO "yyyy-MM-ddTHH:mm". Accepte la valeur enveloppée
 * (`{ "V": … }`, la forme habituelle), le tableau [y, m, d, h, min] et les
 * chaînes "yyyy-MM-dd HH:mm:ss" / "dd/MM/yyyy HH:mm".
 */
private fun decodePronoteDateTime(value: Any?): String? = when (value) {
    is org.json.JSONObject -> decodePronoteDateTime(value.opt("V"))
    is org.json.JSONArray -> {
        if (value.length() < 3) {
            null
        } else {
            val year = value.optInt(0, 0)
            val month = value.optInt(1, 0)
            val day = value.optInt(2, 0)
            if (year <= 0 || month !in 1..12 || day !in 1..31) {
                null
            } else {
                // `LocalDateTime.toString()` (et non un format explicite) pour
                // produire EXACTEMENT la même forme que la branche chaîne :
                // les secondes nulles disparaissent des deux côtés.
                try {
                    java.time.LocalDateTime.of(
                        year, month, day, value.optInt(3, 0), value.optInt(4, 0),
                    ).toString()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    is String -> {
        val raw = value.trim()
        val iso = try {
            java.time.LocalDateTime.parse(raw.replace(' ', 'T').take(19))
        } catch (_: Exception) {
            null
        } ?: try {
            java.time.LocalDateTime.parse(
                raw,
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm[:ss]"),
            )
        } catch (_: Exception) {
            null
        }
        iso?.withSecond(0)?.toString()
    }
    else -> null
}
