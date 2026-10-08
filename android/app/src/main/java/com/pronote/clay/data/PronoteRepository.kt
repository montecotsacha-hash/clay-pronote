package com.pronote.clay.data

import com.pronote.clay.core.AverageCalculator
import com.pronote.clay.core.Logger
import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import com.pronote.clay.data.local.LocalDataSource
import com.pronote.clay.data.remote.PronoteCompatibilityLayer
import com.pronote.clay.data.remote.PronoteRawPayload
import com.pronote.clay.data.security.SecureCredentialsManager
import com.pronote.clay.domain.AverageSource
import com.pronote.clay.domain.GeneralAverage
import com.pronote.clay.domain.HistoryPoint
import com.pronote.clay.domain.PronoteSession
import com.pronote.clay.domain.SchoolSnapshot
import com.pronote.clay.domain.SubjectAverage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A grade is worth showing on the history chart when its date is parsable and
 * at most [maxAgeDays] days old. An unparsable date keeps the point visible:
 * dropping it would silently erase history for a format quirk.
 */
fun isWithinPeriod(isoDate: String, todayMs: Long, maxAgeDays: Long): Boolean {
    val millis = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.time
    } catch (_: Exception) {
        null
    } ?: return true
    return millis >= todayMs - maxAgeDays * HistoryPeriods.DAY_MS
}

/** Chart periods, from one week to the full (school) year. */
object HistoryPeriods {
    const val WEEK_DAYS = 7L
    const val MONTH_DAYS = 30L
    const val QUARTER_DAYS = 90L
    const val YEAR_DAYS = 365L

    const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Nothing is filtered out beyond the year view. */
    const val ALL_DAYS = Long.MAX_VALUE
}

/**
 * [PronoteRepository] — the abstraction the rest of the app depends on.
 *
 * The repository decides whether data comes from the local cache, from Pronote,
 * or from both (cache-first + background refresh).
 */
interface PronoteRepository {
    suspend fun login(
        accountId: String,
        schoolUrl: String,
        username: String,
        password: String,
    ): PronoteResult<PronoteSession>

    suspend fun refreshSession(accountId: String): PronoteSession?

    fun getCachedSnapshot(accountId: String): SchoolSnapshot?

    suspend fun sync(accountId: String): PronoteResult<SchoolSnapshot>

    suspend fun logout(accountId: String)

    suspend fun deleteLocalData(accountId: String)

    /**
     * History narrowed to a period (7 days, 1 month, …) for the chart.
     * Cache-only: never touches the network.
     */
    fun getHistory(accountId: String, days: Long): List<HistoryPoint>
}

class PronoteRepositoryImpl(
    private val connector: PronoteCompatibilityLayer,
    private val sessions: SessionManager,
    private val local: LocalDataSource,
    /** Notified after every cache write so the widgets never show stale data. */
    private val onSnapshotSaved: () -> Unit = {},
) : PronoteRepository {

    /** Cache-first: the caller renders this immediately, before any network. */
    override fun getCachedSnapshot(accountId: String): SchoolSnapshot? =
        local.loadSnapshot(accountId)

    override suspend fun login(
        accountId: String,
        schoolUrl: String,
        username: String,
        password: String,
    ): PronoteResult<PronoteSession> {
        val result = connector.authenticate(schoolUrl, username, password)
        if (result is PronoteResult.Failure) return result

        val auth = (result as PronoteResult.Success).value
        val session = PronoteSession(
            token = auth.token,
            expiresAt = auth.expiresAt,
            schoolUrl = schoolUrl,
            schoolName = auth.schoolName,
            connected = true,
        )

        // Prefer the token; keep the password only if the connector gave no token.
        sessions.save(
            accountId,
            SecureCredentialsManager.StoredCredentials(
                schoolUrl = schoolUrl,
                schoolName = auth.schoolName,
                username = username,
                token = auth.token.takeIf { it.isNotEmpty() },
                expiresAt = auth.expiresAt,
                deviceUuid = auth.deviceUuid,
                // Never persist an account password when the connector issues
                // its own encrypted mobile refresh token.
                password = password.takeIf { auth.token.isEmpty() },
            ),
        )
        local.saveSessionMeta(accountId, session)
        local.saveSnapshot(accountId, toSnapshot(auth.payload))
        onSnapshotSaved()
        return PronoteResult.Success(session)
    }

    override suspend fun refreshSession(accountId: String): PronoteSession? =
        sessions.refreshSession(accountId)

    override suspend fun sync(accountId: String): PronoteResult<SchoolSnapshot> {
        val stored = sessions.load(accountId)
            ?: return PronoteResult.Failure(PronoteError.SessionExpired)

        val result = connector.fetchAll(
            token = stored.token.orEmpty(),
            schoolUrl = stored.schoolUrl,
            username = stored.username,
            deviceUuid = stored.deviceUuid.orEmpty(),
        )
        if (result is PronoteResult.Failure) return result

        val snapshot = toSnapshot((result as PronoteResult.Success).value)
        local.saveSnapshot(accountId, snapshot)
        onSnapshotSaved()
        Logger.info("Grades updated")
        return PronoteResult.Success(snapshot)
    }

    override fun getHistory(accountId: String, days: Long): List<HistoryPoint> {
        val snapshot = local.loadSnapshot(accountId) ?: return emptyList()
        return filterHistory(snapshot.history, days)
    }

    override suspend fun logout(accountId: String) {
        sessions.load(accountId)?.token?.let { connector.logout(it) }
        sessions.clearSession(accountId)
        local.clearSessionMeta(accountId)
    }

    /** "Supprimer mes données" — snapshot, session metadata and secrets. */
    override suspend fun deleteLocalData(accountId: String) {
        local.clearAccount(accountId)
        local.clearSessionMeta(accountId)
        sessions.clearSession(accountId)
    }

    /**
     * Remote payload -> local snapshot.
     *
     * The general average is OFFICIAL when Pronote provides one, otherwise it is
     * CALCULATED locally so a hidden average still shows up.
     */
    fun toSnapshot(payload: PronoteRawPayload): SchoolSnapshot {
        val student = Mappers.mapStudent(payload.student)
        val subjects = payload.subjects.mapIndexed { index, dto -> Mappers.mapSubject(dto, index) }

        // Grade DTOs reference subjects by connector id: remap onto domain ids.
        val idMap = payload.subjects.mapIndexed { index, dto ->
            (dto.id ?: "subject-$index") to subjects[index].id
        }.toMap()

        val grades = payload.grades.mapIndexed { index, dto ->
            val grade = Mappers.mapGrade(dto, index)
            grade.copy(subjectId = idMap[dto.matiere] ?: grade.subjectId)
        }

        val remoteAverages = payload.subjectAverages.map { dto ->
            val mapped = Mappers.mapSubjectAverage(dto)
            mapped.copy(subjectId = idMap[dto.matiere] ?: mapped.subjectId)
        }

        val subjectAverages = subjects.map { subject ->
            val remote = remoteAverages.find { it.subjectId == subject.id }
            val computed = AverageCalculator.subjectAverage(subject, grades)
            SubjectAverage(
                subjectId = subject.id,
                value = remote?.value ?: computed ?: 0.0,
                period = remote?.period ?: payload.period,
                source = if (remote != null) AverageSource.OFFICIAL else AverageSource.CALCULATED,
            )
        }

        val computed = AverageCalculator.calculateGeneralAverage(
            official = payload.generalAverage,
            subjects = subjects,
            grades = grades,
            subjectAverages = remoteAverages,
        )

        val generalAverage = computed?.let { (value, isCalculated) ->
            GeneralAverage(
                value = value,
                period = payload.period,
                source = if (isCalculated) AverageSource.CALCULATED else AverageSource.OFFICIAL,
                updatedAt = System.currentTimeMillis(),
            )
        }

        return SchoolSnapshot(
            student = student,
            subjects = subjects,
            grades = grades,
            subjectAverages = subjectAverages,
            generalAverage = generalAverage,
            history = buildHistory(grades, generalAverage),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** A compact history built from the grades we know about. */
    private fun buildHistory(
        grades: List<com.pronote.clay.domain.Grade>,
        current: GeneralAverage?,
    ): List<HistoryPoint> {
        val sorted = grades.sortedBy { it.date }
        if (sorted.isEmpty()) return emptyList()

        val points = mutableListOf<HistoryPoint>()
        val running = mutableListOf<com.pronote.clay.domain.Grade>()
        var lastBucket = ""

        for (grade in sorted) {
            running.add(grade)
            val bucket = grade.date.take(7) // month
            val value = runningAverage(running)
            if (bucket != lastBucket && value != null) {
                points.add(HistoryPoint(grade.date, value))
                lastBucket = bucket
            }
        }

        current?.let { avg ->
            points.add(HistoryPoint(todayIso(), avg.value))
        }

        return points
    }

    private fun runningAverage(grades: List<com.pronote.clay.domain.Grade>): Double? {
        val significant = AverageCalculator.significantGrades(grades)
        if (significant.isEmpty()) return null
        val rows = significant.map {
            AverageCalculator.normalizeGrade(it) to it.coefficient
        }
        return AverageCalculator.weightedMean(rows)
    }

    private fun todayIso(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        /**
         * Keeps only the history points from the last [days] days. The most
         * recent point (the snapshot's "today" value) always stays, so the
         * chart ends on the current average at every period.
         */
        fun filterHistory(history: List<HistoryPoint>, days: Long, todayMs: Long = System.currentTimeMillis()): List<HistoryPoint> {
            if (history.isEmpty()) return emptyList()
            if (days >= HistoryPeriods.YEAR_DAYS) return history
            val scoped = history.filter {
                isWithinPeriod(it.date, todayMs, days) || it == history.last()
            }
            // Too few points for the window: keep the two most recent ones
            // so the chart stays readable without jumping back to the full
            // history — a week view must not look like the year view.
            return if (scoped.size >= MIN_POINTS_PER_WINDOW) scoped else history.takeLast(MIN_POINTS_PER_WINDOW)
        }

        /** Below that a window is unreadable; show the full history instead. */
        const val MIN_POINTS_PER_WINDOW = 2
    }
}
