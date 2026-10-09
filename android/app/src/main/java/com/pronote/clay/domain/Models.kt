package com.pronote.clay.domain

/**
 * Domain models — completely independent from the Pronote connector.
 * The UI never touches a DTO or a storage entity directly.
 */

data class Student(
    val id: String,
    val firstName: String,
    val lastName: String,
    val className: String,
    val schoolName: String,
)

data class Subject(
    val id: String,
    val name: String,
    val shortName: String,
    val teacher: String,
    val coefficient: Double,
    /** Index into the clay accent palette. */
    val colorIndex: Int,
)

data class Grade(
    val id: String,
    val subjectId: String,
    val title: String,
    val value: Double,
    val maxValue: Double,
    val coefficient: Double,
    /** ISO date, YYYY-MM-DD. */
    val date: String,
    val comment: String? = null,
    /** false for grades the teacher marked "non significatif". */
    val countsTowardAverage: Boolean = true,
)

/** Where an average came from — never blur the two. */
enum class AverageSource { OFFICIAL, CALCULATED }

data class SubjectAverage(
    val subjectId: String,
    val value: Double,
    val period: String,
    val source: AverageSource,
)

data class GeneralAverage(
    val value: Double,
    val period: String,
    val source: AverageSource,
    val updatedAt: Long,
)

data class HistoryPoint(val date: String, val value: Double)

/**
 * One course of the timetable, already normalized for the UI.
 * Times are local "HH:mm", dates ISO "yyyy-MM-dd".
 */
data class TimetableLesson(
    val subjectId: String?,
    val subjectName: String,
    val teacher: String? = null,
    val room: String? = null,
    val group: String? = null,
    /** ISO datetime start, e.g. "2026-10-08T08:00:00". */
    val startIso: String,
    /** ISO datetime end. */
    val endIso: String,
    val cancelled: Boolean = false,
    /** Detention, test label… free text under the subject. */
    val memo: String? = null,
) {
    /** "08:00". */
    val startTime: String get() = startIso.take(11 + 5).substring(11, 16)

    /** "09:00". */
    val endTime: String get() = endIso.take(16).substring(11, 16)

    /** ISO date of the course day. */
    val day: String get() = startIso.take(10)

    /** Duration in minutes — used to size the cards. */
    val durationMinutes: Int
        get() = runCatching {
            val fmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val s = java.time.LocalDateTime.parse(startIso, fmt)
            val e = java.time.LocalDateTime.parse(endIso, fmt)
            java.time.Duration.between(s, e).toMinutes().toInt()
        }.getOrDefault(60)
}

data class PronoteSession(
    /** Opaque token — never rendered, never logged. */
    val token: String,
    val expiresAt: Long,
    val schoolUrl: String,
    val schoolName: String,
    val connected: Boolean,
)

/** The whole school snapshot stored locally (cache-first rendering). */
data class SchoolSnapshot(
    val student: Student,
    val subjects: List<Subject>,
    val grades: List<Grade>,
    val subjectAverages: List<SubjectAverage>,
    val generalAverage: GeneralAverage?,
    val history: List<HistoryPoint>,
    val fetchedAt: Long,
)

/** Non-sensitive preferences (DataStore equivalent). */
data class Preferences(
    val autoSync: Boolean = true,
    val notifyNewGrades: Boolean = true,
    val notifyAverageChange: Boolean = true,
    val darkTheme: Boolean? = null, // null = follow system
    val dynamicColor: Boolean = true,
    val biometricLock: Boolean = false,
    /** Name shown after "Bonjour "; takes priority over the Pronote first name. */
    val displayName: String? = null,
)
