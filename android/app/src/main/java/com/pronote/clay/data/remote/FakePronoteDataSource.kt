package com.pronote.clay.data.remote

import com.pronote.clay.core.Logger
import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * FakePronoteDataSource (FakePronoteRepository at connector level).
 *
 * Lets the whole app be developed and demoed without a live Pronote account.
 * It mimics the real connector's failure modes so the error UI can be tested:
 *  - wrong password   -> InvalidCredentials
 *  - unreachable host -> PronoteUnavailable
 *  - "captcha" in URL -> CaptchaRequired
 */
class FakePronoteDataSource : PronoteDataSource {

    override val strategy: String = "DirectPronoteStrategy"

    private fun isoDaysAgo(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    /** The mock school data — close to the product's reference example. */
    private fun buildPayload(schoolUrl: String): PronoteRawPayload {
        val schoolName =
            if (schoolUrl.contains("index-education")) "Lycée Jean Moulin"
            else "Établissement de démonstration"

        return PronoteRawPayload(
            student = PronoteStudentDto(
                id = "student-1",
                prenom = "Alex",
                nom = "Martin",
                classe = "1re Générale — G2",
                etablissement = schoolName,
            ),
            subjects = listOf(
                PronoteSubjectDto("maths", "Mathématiques", "MAT", "Mme Dubois", 6.0),
                PronoteSubjectDto("francais", "Français", "FRA", "M. Leroy", 4.0),
                PronoteSubjectDto("anglais", "Anglais", "ANG", "Ms. Carter", 3.0),
                PronoteSubjectDto("physique", "Physique-Chimie", "PHC", "M. Bernard", 5.0),
                PronoteSubjectDto("histoire", "Histoire-Géo", "HGE", "Mme Petit", 3.0),
                PronoteSubjectDto("ses", "SES", "SES", "M. Roux", 2.0),
            ),
            grades = listOf(
                PronoteGradeDto("g1", "maths", "Contrôle fonctions", 17.0, 20.0, 2.0, isoDaysAgo(1), "Très bon travail."),
                PronoteGradeDto("g2", "maths", "DM — Suites", 15.0, 20.0, 1.0, isoDaysAgo(6)),
                PronoteGradeDto("g3", "maths", "Interrogation dérivées", 16.0, 20.0, 1.0, isoDaysAgo(13)),
                PronoteGradeDto("g4", "maths", "Évaluation probabilités", 8.0, 10.0, 1.0, isoDaysAgo(21)),
                PronoteGradeDto("g5", "francais", "Commentaire composé", 14.0, 20.0, 2.0, isoDaysAgo(2)),
                PronoteGradeDto("g6", "francais", "Dissertation", 15.0, 20.0, 2.0, isoDaysAgo(18)),
                PronoteGradeDto("g7", "francais", "Oral", 15.5, 20.0, 1.0, isoDaysAgo(30)),
                PronoteGradeDto("g8", "anglais", "Compréhension orale", 18.0, 20.0, 1.0, isoDaysAgo(3)),
                PronoteGradeDto("g9", "anglais", "Expression écrite", 16.0, 20.0, 2.0, isoDaysAgo(15)),
                PronoteGradeDto("g10", "anglais", "Thème", 17.0, 20.0, 1.0, isoDaysAgo(24)),
                PronoteGradeDto("g11", "physique", "TP dosage", 16.0, 20.0, 1.0, isoDaysAgo(4)),
                PronoteGradeDto("g12", "physique", "Contrôle ondes", 15.0, 20.0, 3.0, isoDaysAgo(11)),
                PronoteGradeDto("g13", "physique", "Interrogation énergie", 8.5, 10.0, 1.0, isoDaysAgo(26)),
                PronoteGradeDto("g14", "histoire", "Composition", 13.0, 20.0, 2.0, isoDaysAgo(8)),
                PronoteGradeDto("g15", "histoire", "Carte mentale", 15.0, 20.0, 1.0, isoDaysAgo(19)),
                PronoteGradeDto("g16", "ses", "Exposé marché", 17.0, 20.0, 1.0, isoDaysAgo(9)),
                PronoteGradeDto("g17", "ses", "Devoir maison", 12.0, 20.0, 1.0, isoDaysAgo(22)),
                // "non significatif" grade — must not be counted anywhere.
                PronoteGradeDto(
                    "g18", "anglais", "Test de positionnement", 5.0, 20.0, 1.0,
                    isoDaysAgo(40), nonSignificatif = true,
                ),
            ),
            subjectAverages = listOf(
                PronoteSubjectAverageDto("maths", 16.4, "T2"),
                PronoteSubjectAverageDto("francais", 14.8, "T2"),
                PronoteSubjectAverageDto("anglais", 17.2, "T2"),
                PronoteSubjectAverageDto("physique", 15.6, "T2"),
                PronoteSubjectAverageDto("histoire", 13.7, "T2"),
                PronoteSubjectAverageDto("ses", 15.1, "T2"),
            ),
            // Demo mode: Pronote returns no official general average, so the app
            // has to compute it locally — the "hidden average" scenario.
            generalAverage = null,
            period = "T2",
        )
    }

    override suspend fun authenticate(
        schoolUrl: String,
        username: String,
        password: String,
    ): PronoteResult<PronoteAuthResult> {
        delay(550)
        if (!schoolUrl.startsWith("http")) return PronoteResult.Failure(PronoteError.InvalidPronoteUrl)
        if (schoolUrl.contains("captcha", ignoreCase = true) || schoolUrl.contains("ent.")) {
            return PronoteResult.Failure(PronoteError.CaptchaRequired)
        }
        if (username.isBlank() || password.length < 3) {
            return PronoteResult.Failure(PronoteError.InvalidCredentials)
        }
        if (password.contains("erreur", ignoreCase = true) || password.contains("indispo", ignoreCase = true)) {
            return PronoteResult.Failure(PronoteError.PronoteUnavailable)
        }

        val payload = buildPayload(schoolUrl)
        Logger.info("Sync started")
        return PronoteResult.Success(
            PronoteAuthResult(
                token = "fake-token-${System.currentTimeMillis()}",
                expiresAt = System.currentTimeMillis() + 6 * 60 * 60 * 1000,
                schoolName = payload.student.etablissement ?: "Établissement",
                payload = payload,
            ),
        )
    }

    override suspend fun restoreSession(
        token: String,
        schoolUrl: String,
    ): PronoteResult<PronoteAuthResult> {
        delay(300)
        if (!token.startsWith("fake-token-")) return PronoteResult.Failure(PronoteError.SessionExpired)

        val payload = buildPayload(schoolUrl)
        return PronoteResult.Success(
            PronoteAuthResult(
                token = token,
                expiresAt = System.currentTimeMillis() + 6 * 60 * 60 * 1000,
                schoolName = payload.student.etablissement ?: "Établissement",
                payload = payload,
            ),
        )
    }

    override suspend fun fetchAll(
        token: String,
        schoolUrl: String,
    ): PronoteResult<PronoteRawPayload> {
        delay(420)
        if (token.isBlank()) return PronoteResult.Failure(PronoteError.SessionExpired)

        val payload = buildPayload(schoolUrl)
        // Occasionally introduce a brand-new grade so "nouvelle note" detection
        // and the change-of-average notification can be demonstrated.
        val withNews = if (Math.random() < 0.45) {
            payload.copy(
                grades = listOf(
                    PronoteGradeDto(
                        "g-new-${System.currentTimeMillis()}",
                        "maths",
                        "Nouvelle évaluation",
                        19.0,
                        20.0,
                        2.0,
                        isoDaysAgo(0),
                        "Excellent.",
                    ),
                ) + payload.grades,
                subjectAverages = payload.subjectAverages.map {
                    if (it.matiere == "maths") it.copy(moyenne = 16.9) else it
                },
            )
        } else payload

        Logger.info("Sync completed")
        return PronoteResult.Success(withNews)
    }

    override suspend fun logout(token: String) {
        delay(120)
    }
}
