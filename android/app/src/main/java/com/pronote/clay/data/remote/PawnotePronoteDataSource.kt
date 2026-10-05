package com.pronote.clay.data.remote

import ink.literate.pawnote.api.gradesOverview
import ink.literate.pawnote.api.helpers.loginCredentials
import ink.literate.pawnote.api.helpers.loginToken
import ink.literate.pawnote.models.AccountKind
import ink.literate.pawnote.models.CredentialsAuth
import ink.literate.pawnote.models.GradeKind
import ink.literate.pawnote.models.SessionHandle
import ink.literate.pawnote.models.TabLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Direct, on-device Pronote connector backed by Pawnote.
 * Credentials and school data go from this app directly to the supplied school;
 * no Ardoise server handles them. Only Pawnote's refresh token and device UUID
 * are persisted by the encrypted local credentials vault.
 */
class PawnotePronoteDataSource : PronoteDataSource {

    override val strategy: String = "Pawnote — connexion directe"

    /** Live Ktor sessions are process-local; only Pawnote refresh tokens persist. */
    private data class ActiveSession(val refreshToken: String, val handle: SessionHandle)

    private val sessions = ConcurrentHashMap<String, ActiveSession>()

    override suspend fun authenticate(
        schoolUrl: String,
        username: String,
        password: String,
    ): com.pronote.clay.core.PronoteResult<PronoteAuthResult> = withContext(Dispatchers.IO) {
        val deviceUuid = UUID.randomUUID().toString()
        try {
            val login = loginCredentials(
                CredentialsAuth(
                    url = schoolUrl,
                    username = username,
                    password = password,
                    kind = AccountKind.STUDENT,
                    deviceUUID = deviceUuid,
                ),
            )
            val payload = readSnapshot(login.session)
            val key = sessionKey(schoolUrl, username)
            sessions.put(key, ActiveSession(login.refreshInfo.token, login.session))
            com.pronote.clay.core.PronoteResult.Success(
                PronoteAuthResult(
                    token = login.refreshInfo.token,
                    expiresAt = Long.MAX_VALUE,
                    schoolName = login.session.userResource.establishmentName,
                    payload = payload,
                    deviceUuid = deviceUuid,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            com.pronote.clay.core.PronoteResult.Failure(mapError(error))
        }
    }

    override suspend fun restoreSession(
        token: String,
        schoolUrl: String,
        username: String,
        deviceUuid: String,
    ): com.pronote.clay.core.PronoteResult<PronoteAuthResult> = withContext(Dispatchers.IO) {
        connectWithToken(token, schoolUrl, username, deviceUuid)
    }

    override suspend fun fetchAll(
        token: String,
        schoolUrl: String,
        username: String,
        deviceUuid: String,
    ): com.pronote.clay.core.PronoteResult<PronoteRawPayload> = withContext(Dispatchers.IO) {
        val active = sessions[sessionKey(schoolUrl, username)]
            ?.takeIf { it.refreshToken == token }
        if (active != null) {
            try {
                com.pronote.clay.core.PronoteResult.Success(readSnapshot(active.handle))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                com.pronote.clay.core.PronoteResult.Failure(mapError(error))
            }
        } else {
            when (val restored = connectWithToken(token, schoolUrl, username, deviceUuid)) {
                is com.pronote.clay.core.PronoteResult.Success ->
                    com.pronote.clay.core.PronoteResult.Success(restored.value.payload)
                is com.pronote.clay.core.PronoteResult.Failure -> restored
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
    ): com.pronote.clay.core.PronoteResult<PronoteAuthResult> = try {
        val login = loginToken(
            CredentialsAuth(
                url = schoolUrl,
                username = username,
                token = token,
                kind = AccountKind.STUDENT,
                deviceUUID = deviceUuid,
            ),
        )
        val payload = readSnapshot(login.session)
        val key = sessionKey(schoolUrl, username)
        sessions.put(key, ActiveSession(login.refreshInfo.token, login.session))
        com.pronote.clay.core.PronoteResult.Success(
            PronoteAuthResult(
                token = login.refreshInfo.token,
                expiresAt = Long.MAX_VALUE,
                schoolName = login.session.userResource.establishmentName,
                payload = payload,
                deviceUuid = deviceUuid,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        com.pronote.clay.core.PronoteResult.Failure(mapError(error))
    }

    private suspend fun readSnapshot(session: SessionHandle): PronoteRawPayload {
        val tab = session.userResource.tabs[TabLocation.Grades]
            ?: error("Aucune période de notes accessible sur ce compte.")
        val period = tab.defaultPeriod ?: tab.periods.first()
        val overview = gradesOverview(session.information, period)

        val subjectMap = LinkedHashMap<String, PronoteSubjectDto>()
        overview.subjectsAverages.forEach { average ->
            subjectMap[average.subject.id] = PronoteSubjectDto(
                id = average.subject.id,
                nom = average.subject.name,
                nomCourt = average.subject.name.take(3).uppercase(),
                coefficient = 1.0,
            )
        }
        overview.grades.forEach { grade ->
            subjectMap.putIfAbsent(
                grade.subject.id,
                PronoteSubjectDto(
                    id = grade.subject.id,
                    nom = grade.subject.name,
                    nomCourt = grade.subject.name.take(3).uppercase(),
                    coefficient = 1.0,
                ),
            )
        }

        val grades = overview.grades.map { grade ->
            PronoteGradeDto(
                id = grade.id,
                matiere = grade.subject.id,
                intitule = grade.comment.takeIf { it.isNotBlank() } ?: grade.subject.name,
                note = grade.value.points ?: 0.0,
                bareme = grade.outOf.points ?: 20.0,
                coefficient = grade.coefficient,
                date = "%04d-%02d-%02d".format(
                    grade.date.year,
                    grade.date.monthNumber,
                    grade.date.dayOfMonth,
                ),
                commentaire = grade.comment,
                nonSignificatif = grade.value.kind != GradeKind.Grade,
            )
        }

        val averages = overview.subjectsAverages.mapNotNull { average ->
            average.student?.points?.let { value ->
                PronoteSubjectAverageDto(
                    matiere = average.subject.id,
                    moyenne = value,
                    periode = period.name,
                )
            }
        }

        val fullName = session.user.name.trim()
        val firstName = fullName.substringBeforeLast(' ', fullName)
        val lastName = fullName.substringAfterLast(' ', "")

        return PronoteRawPayload(
            student = PronoteStudentDto(
                id = session.userResource.id,
                prenom = firstName,
                nom = lastName,
                classe = session.userResource.className.orEmpty(),
                etablissement = session.userResource.establishmentName,
            ),
            subjects = subjectMap.values.toList(),
            grades = grades,
            subjectAverages = averages,
            generalAverage = overview.overallAverage?.points,
            period = period.name,
        )
    }

    private fun sessionKey(schoolUrl: String, username: String) = "$schoolUrl|$username"

    private fun mapError(error: Throwable): com.pronote.clay.core.PronoteError = when {
        error is ink.literate.pawnote.models.errors.BadCredentialsError ->
            com.pronote.clay.core.PronoteError.InvalidCredentials
        error is ink.literate.pawnote.models.errors.SecurityError ->
            com.pronote.clay.core.PronoteError.CaptchaRequired
        error is ink.literate.pawnote.models.errors.SuspendedIPError ||
            error is ink.literate.pawnote.models.errors.BusyPageError ||
            error is ink.literate.pawnote.models.errors.RateLimitedError ->
            com.pronote.clay.core.PronoteError.PronoteUnavailable
        error is ink.literate.pawnote.models.errors.PageUnavailableError ->
            com.pronote.clay.core.PronoteError.InvalidPronoteUrl
        else -> com.pronote.clay.core.PronoteError.UnknownError
    }
}
