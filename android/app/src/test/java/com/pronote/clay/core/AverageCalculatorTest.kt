package com.pronote.clay.core

import com.pronote.clay.domain.AverageSource
import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.Subject
import com.pronote.clay.domain.SubjectAverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure calculation layer the home screen renders.
 */
class AverageCalculatorTest {

    private val maths = Subject(
        id = "maths",
        name = "Mathématiques",
        shortName = "MAT",
        teacher = "Mme Dubois",
        coefficient = 6.0,
        colorIndex = 0,
    )

    private fun grade(
        id: String,
        value: Double,
        max: Double = 20.0,
        coef: Double = 1.0,
        counts: Boolean = true,
        date: String = "2026-01-10",
    ) = Grade(
        id = id,
        subjectId = "maths",
        title = "Devoir",
        value = value,
        maxValue = max,
        coefficient = coef,
        date = date,
        countsTowardAverage = counts,
    )

    @Test
    fun `normalises a grade onto the 20 scale`() {
        assertEquals(16.0, AverageCalculator.normalizeGrade(grade("a", 8.0, max = 10.0)), 0.001)
    }

    @Test
    fun `excludes grades marked non significant`() {
        val grades = listOf(grade("a", 12.0), grade("b", 20.0, counts = false))
        assertEquals(12.0, AverageCalculator.subjectAverage(maths, grades)!!, 0.001)
    }

    @Test
    fun `weights the subject mean by coefficient`() {
        val grades = listOf(grade("a", 10.0, coef = 1.0), grade("b", 20.0, coef = 3.0))
        assertEquals(17.5, AverageCalculator.subjectAverage(maths, grades)!!, 0.001)
    }

    @Test
    fun `returns null when every grade is excluded`() {
        val grades = listOf(grade("a", 10.0, counts = false))
        assertNull(AverageCalculator.subjectAverage(maths, grades))
    }

    @Test
    fun `keeps the official average when Pronote provides one`() {
        val result = AverageCalculator.calculateGeneralAverage(
            official = 15.77,
            subjects = listOf(maths),
            grades = emptyList(),
            subjectAverages = emptyList(),
        )
        assertEquals(15.77, result!!.first, 0.001)
        assertEquals(false, result.second)
    }

    @Test
    fun `recalculates a hidden average from the subject averages`() {
        val averages = listOf(
            SubjectAverage("maths", 16.0, "T2", AverageSource.OFFICIAL),
        )
        val result = AverageCalculator.calculateGeneralAverage(
            official = null,
            subjects = listOf(maths),
            grades = emptyList(),
            subjectAverages = averages,
        )
        assertEquals(16.0, result!!.first, 0.001)
        assertTrue(result.second)
    }

    @Test
    fun `formats averages deltas and grades with french decimals`() {
        assertEquals("15,8", AverageCalculator.formatAverage(15.75))
        assertEquals("+0,5", AverageCalculator.formatDelta(0.5))
        assertEquals("−1,0", AverageCalculator.formatDelta(-1.0))
        assertEquals("0,0", AverageCalculator.formatDelta(0.0))
        assertEquals("8/10", AverageCalculator.formatGrade(8.0, 10.0))
        assertEquals("15,5/20", AverageCalculator.formatGrade(15.5, 20.0))
    }

    @Test
    fun `trims coefficients for the UI`() {
        assertEquals("2", AverageCalculator.trim(2.0))
        assertEquals("1,5", AverageCalculator.trim(1.5))
    }

    @Test
    fun `reports an improving subject trend`() {
        val grades = listOf(
            grade("recent", 18.0, date = "2026-02-01"),
            grade("older", 10.0, date = "2026-01-01"),
        )
        assertEquals(8.0, AverageCalculator.subjectTrend(maths, grades)!!, 0.001)
    }
}