package com.pronote.clay.domain

import com.pronote.clay.core.AverageCalculator
import com.pronote.clay.core.PronoteResult
import com.pronote.clay.data.PronoteRepository

/**
 * Use cases — the only business entry point for ViewModels.
 * ViewModels must never talk to the connector, the vault or the local cache.
 */

class LoginUseCase(private val repository: PronoteRepository) {
    suspend fun execute(
        accountId: String,
        schoolUrl: String,
        username: String,
        password: String,
    ): PronoteResult<PronoteSession> =
        repository.login(accountId, schoolUrl, username, password)
}

class LogoutUseCase(private val repository: PronoteRepository) {
    suspend fun execute(accountId: String) = repository.logout(accountId)
}

class GetCachedDataUseCase(private val repository: PronoteRepository) {
    fun execute(accountId: String): SchoolSnapshot? = repository.getCachedSnapshot(accountId)
}

class SyncPronoteUseCase(private val repository: PronoteRepository) {
    suspend fun execute(accountId: String) = repository.sync(accountId)
}

/** Timetable of one week, network-backed with a short repository cache. */
class GetTimetableUseCase(private val repository: PronoteRepository) {
    suspend fun execute(
        accountId: String,
        weekStartIso: String,
    ): PronoteResult<List<TimetableLesson>> =
        repository.getTimetable(accountId, weekStartIso)
}

class RefreshSessionUseCase(private val repository: PronoteRepository) {
    suspend fun execute(accountId: String) = repository.refreshSession(accountId)
}

class DeleteLocalDataUseCase(private val repository: PronoteRepository) {
    suspend fun execute(accountId: String) = repository.deleteLocalData(accountId)
}

class GetStudentUseCase {
    fun execute(snapshot: SchoolSnapshot?): Student? = snapshot?.student
}

class GetSubjectsUseCase {
    fun execute(snapshot: SchoolSnapshot?): List<Subject> = snapshot?.subjects.orEmpty()
}

class GetGradesUseCase {
    fun execute(snapshot: SchoolSnapshot?, subjectId: String? = null): List<Grade> {
        val grades = snapshot?.grades ?: return emptyList()
        val scoped = if (subjectId != null) grades.filter { it.subjectId == subjectId } else grades
        return scoped.sortedByDescending { it.date }
    }
}

class GetAveragesUseCase {
    fun execute(snapshot: SchoolSnapshot?): List<SubjectAverage> = snapshot?.subjectAverages.orEmpty()
}

/** UI-facing summary of the general average. */
data class AverageSummary(
    val value: Double,
    val source: AverageSource,
    val display: String,
    val delta: Double?,
    val deltaDisplay: String?,
)

class CalculateGeneralAverageUseCase {
    fun execute(snapshot: SchoolSnapshot?): AverageSummary? {
        val average = snapshot?.generalAverage ?: return null
        val history = snapshot.history
        val delta = if (history.size > 1) {
            AverageCalculator.round2(average.value - history[history.size - 2].value)
        } else null

        return AverageSummary(
            value = average.value,
            source = average.source,
            display = AverageCalculator.formatAverage(average.value),
            delta = delta,
            deltaDisplay = delta?.let { AverageCalculator.formatDelta(it) },
        )
    }
}

/** One row of the subjects list. */
data class SubjectRow(
    val subject: Subject,
    val average: Double?,
    val display: String,
    val delta: Double?,
    val deltaDisplay: String?,
    val gradeCount: Int,
    val source: AverageSource,
)

class GetSubjectRowsUseCase {
    fun execute(snapshot: SchoolSnapshot?): List<SubjectRow> {
        if (snapshot == null) return emptyList()
        val bySubject = snapshot.subjectAverages.associateBy { it.subjectId }

        return snapshot.subjects.map { subject ->
            val remote = bySubject[subject.id]
            val computed = AverageCalculator.subjectAverage(subject, snapshot.grades)
            val average = remote?.value ?: computed
            val delta = AverageCalculator.subjectTrend(subject, snapshot.grades)

            SubjectRow(
                subject = subject,
                average = average,
                display = average?.let { AverageCalculator.formatAverage(it) } ?: "—",
                delta = delta,
                deltaDisplay = delta?.let { AverageCalculator.formatDelta(it) },
                gradeCount = snapshot.grades.count { it.subjectId == subject.id },
                source = remote?.source ?: AverageSource.CALCULATED,
            )
        }
    }
}
