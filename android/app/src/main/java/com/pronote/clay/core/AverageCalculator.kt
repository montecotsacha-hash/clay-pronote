package com.pronote.clay.core

import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.Subject
import com.pronote.clay.domain.SubjectAverage

/**
 * AverageCalculator — pure, dependency-free, unit tested.
 *
 * PRONOTE does not always use a simple mean: coefficients, "non significatif"
 * grades and per-subject coefficients all matter. When PRONOTE already provides
 * an official average we keep it as OFFICIAL and only compute locally as a
 * fallback.
 */
object AverageCalculator {

    fun normalizeGrade(grade: Grade): Double =
        if (grade.maxValue <= 0.0) 0.0 else (grade.value / grade.maxValue) * 20.0

    fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    fun significantGrades(grades: List<Grade>): List<Grade> =
        grades.filter { it.countsTowardAverage && it.maxValue > 0.0 }

    /** Weighted mean. Weight = coefficient, defaulting to 1. */
    fun weightedMean(rows: List<Pair<Double, Double>>): Double? {
        val usable = rows.filter { !it.first.isNaN() }
        if (usable.isEmpty()) return null
        val totalWeight = usable.sumOf { maxOf(it.second, 0.0001) }
        if (totalWeight <= 0.0) return null
        val weighted = usable.sumOf { it.first * maxOf(it.second, 0.0001) }
        return round2(weighted / totalWeight)
    }

    /** Average of one subject, ignoring grades the teacher marked non significant. */
    fun subjectAverage(subject: Subject, grades: List<Grade>): Double? {
        val scoped = significantGrades(grades).filter { it.subjectId == subject.id }
        if (scoped.isEmpty()) return null
        return weightedMean(scoped.map { normalizeGrade(it) to it.coefficient })
    }

    /** General average rebuilt from the subject averages, weighted by subject coefficient. */
    fun generalAverageFromSubjects(
        subjects: List<Subject>,
        averages: List<SubjectAverage>,
    ): Double? {
        val bySubject = averages.associateBy { it.subjectId }
        val rows = subjects.mapNotNull { subject ->
            val avg = bySubject[subject.id] ?: return@mapNotNull null
            avg.value to (if (subject.coefficient > 0.0) subject.coefficient else 1.0)
        }
        return weightedMean(rows)
    }

    /** Last-resort fallback: every significant grade. */
    fun generalAverageFromGrades(grades: List<Grade>): Double? =
        weightedMean(significantGrades(grades).map { normalizeGrade(it) to it.coefficient })

    /**
     * Computes the general average.
     * @param official average already provided by Pronote, when available.
     */
    fun calculateGeneralAverage(
        official: Double?,
        subjects: List<Subject>,
        grades: List<Grade>,
        subjectAverages: List<SubjectAverage>,
    ): Pair<Double, Boolean>? {
        // 1. Trust the official value when Pronote gives one.
        if (official != null && !official.isNaN()) return round2(official) to false

        // 2. Rebuild from the subject averages.
        generalAverageFromSubjects(subjects, subjectAverages)?.let { return it to true }

        // 3. Last resort: every significant grade.
        generalAverageFromGrades(grades)?.let { return it to true }

        return null
    }

    /**
     * Evolution of a subject: latest grade versus the running average before it.
     * Positive means improving.
     */
    fun subjectTrend(subject: Subject, grades: List<Grade>): Double? {
        val sorted = significantGrades(grades)
            .filter { it.subjectId == subject.id }
            .sortedByDescending { it.date }
        if (sorted.size < 2) return null

        val latest = sorted.first()
        val previous = weightedMean(
            sorted.drop(1).map { normalizeGrade(it) to it.coefficient },
        ) ?: return null

        return round2(normalizeGrade(latest) - previous)
    }

    fun formatAverage(value: Double): String =
        String.format(java.util.Locale.FRANCE, "%.1f", value)

    fun formatDelta(delta: Double): String {
        val sign = when {
            delta > 0.001 -> "+"
            delta < -0.001 -> "−"
            else -> ""
        }
        return sign + String.format(java.util.Locale.FRANCE, "%.1f", Math.abs(delta))
    }

    fun formatGrade(value: Double, maxValue: Double): String {
        val decimals = if (value == Math.floor(value)) 0 else 1
        return String.format(java.util.Locale.FRANCE, "%.${decimals}f/%s", value, trim(maxValue))
    }

    private fun trim(value: Double): String =
        if (value == Math.floor(value)) value.toInt().toString()
        else String.format(java.util.Locale.FRANCE, "%.1f", value)
}
