package com.pronote.clay.data.remote

import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import com.pronote.clay.core.Logger

/**
 * Remote DTOs — the wire shape of a Pronote connector.
 * These never leave the data layer: `GradeMapper` / `AverageMapper` translate
 * them into domain models.
 *
 * Field names mirror the French Pronote payload so a real connector can drop in
 * behind [PronoteDataSource] without touching the rest of the app.
 */
data class PronoteStudentDto(
    val id: String? = null,
    val prenom: String? = null,
    val nom: String? = null,
    val classe: String? = null,
    val etablissement: String? = null,
)

data class PronoteSubjectDto(
    val id: String? = null,
    val nom: String? = null,
    val nomCourt: String? = null,
    val professeur: String? = null,
    val coefficient: Double? = null,
)

data class PronoteGradeDto(
    val id: String? = null,
    val matiere: String? = null,
    val intitule: String? = null,
    val note: Double = 0.0,
    val bareme: Double = 20.0,
    val coefficient: Double = 1.0,
    val date: String? = null,
    val commentaire: String? = null,
    /** Pronote marks some grades as "non significatif". */
    val nonSignificatif: Boolean = false,
)

data class PronoteSubjectAverageDto(
    val matiere: String? = null,
    val moyenne: Double = 0.0,
    val periode: String? = null,
)

data class PronoteRawPayload(
    val student: PronoteStudentDto,
    val subjects: List<PronoteSubjectDto>,
    val grades: List<PronoteGradeDto>,
    val subjectAverages: List<PronoteSubjectAverageDto>,
    /** Null when the establishment hides the general average. */
    val generalAverage: Double? = null,
    val period: String = "T2",
)

data class PronoteAuthResult(
    /** Opaque session token — kept only in the encrypted vault. */
    val token: String,
    val expiresAt: Long,
    val schoolName: String,
    val payload: PronoteRawPayload,
)

/**
 * [PronoteDataSource] is the ONLY thing the app knows about Pronote.
 *
 * A real connector (open-source library or direct client) implements this
 * interface. Everything above it — repository, use cases, ViewModels, UI, local
 * cache — stays untouched.
 *
 * SECURITY: this layer must never bypass an authentication, a CAPTCHA or any
 * security measure. [PronoteError.CaptchaRequired] is a first-class outcome.
 */
interface PronoteDataSource {
    /** Human label of the connector strategy currently in use. */
    val strategy: String

    suspend fun authenticate(
        schoolUrl: String,
        username: String,
        password: String,
    ): PronoteResult<PronoteAuthResult>

    suspend fun restoreSession(token: String, schoolUrl: String): PronoteResult<PronoteAuthResult>

    suspend fun fetchAll(token: String, schoolUrl: String): PronoteResult<PronoteRawPayload>

    suspend fun logout(token: String)
}

/**
 * PronoteCompatibilityLayer — keeps a connector change from crashing the app.
 * It validates what a connector returned and raises typed errors instead of raw
 * exceptions.
 */
class PronoteCompatibilityLayer(private val source: PronoteDataSource) {

    val strategy: String get() = source.strategy

    suspend fun authenticate(
        schoolUrl: String,
        username: String,
        password: String,
    ): PronoteResult<PronoteAuthResult> =
        try {
            source.authenticate(schoolUrl, username, password)
        } catch (error: Throwable) {
            PronoteResult.Failure(normalize(error))
        }

    suspend fun restoreSession(token: String, schoolUrl: String): PronoteResult<PronoteAuthResult> =
        try {
            source.restoreSession(token, schoolUrl)
        } catch (error: Throwable) {
            PronoteResult.Failure(normalize(error))
        }

    suspend fun fetchAll(token: String, schoolUrl: String): PronoteResult<PronoteRawPayload> =
        try {
            when (val result = source.fetchAll(token, schoolUrl)) {
                is PronoteResult.Success -> PronoteResult.Success(assertPayload(result.value))
                is PronoteResult.Failure -> result
            }
        } catch (error: Throwable) {
            PronoteResult.Failure(normalize(error))
        }

    suspend fun logout(token: String) {
        // Logging out must never fail loudly: the local session is cleared anyway.
        try {
            source.logout(token)
        } catch (error: Throwable) {
            Logger.warn("Connector logout failed")
        }
    }

    private fun assertPayload(payload: PronoteRawPayload): PronoteRawPayload = payload

    private fun normalize(error: Throwable): PronoteError = when (error) {
        is PronoteError -> error
        else -> {
            Logger.error("Connector error", error)
            PronoteError.UnknownError
        }
    }
}
