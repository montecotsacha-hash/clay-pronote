package com.pronote.clay.domain

import com.pronote.clay.data.Mappers
import com.pronote.clay.data.remote.PronoteGradeDto
import com.pronote.clay.data.remote.PronoteStudentDto
import com.pronote.clay.data.remote.PronoteSubjectDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the use cases and mappers behind the home screen.
 */
class UseCasesTest {

    private val maths = Subject("maths", "Mathématiques", "MAT", "Mme Dubois", 6.0, 0)
    private val english = Subject("anglais", "Anglais", "ANG", "Ms. Carter", 3.0, 2)

    private fun grade(
        id: String,
        subjectId: String = "maths",
        value: Double = 15.0,
        max: Double = 20.0,
        coef: Double = 1.0,
        counts: Boolean = true,
        date: String = "2026-01-10",
    ) = Grade(
        id = id,
        subjectId = subjectId,
        title = "Devoir $id",
        value = value,
        maxValue = max,
        coefficient = coef,
        date = date,
        countsTowardAverage = counts,
    )

    private fun snapshot(
        grades: List<Grade>,
        averages: List<SubjectAverage> = emptyList(),
        general: GeneralAverage? = null,
        history: List<HistoryPoint> = emptyList(),
    ) = SchoolSnapshot(
        student = Student("s1", "Alex", "Martin", "1re G2", "Lycée Jean Moulin"),
        subjects = listOf(maths, english),
        grades = grades,
        subjectAverages = averages,
        generalAverage = general,
        history = history,
        fetchedAt = 0L,
    )

    @Test
    fun `prefers the official subject average over the local computation`() {
        val rows = GetSubjectRowsUseCase().execute(
            snapshot(
                grades = listOf(grade("a", value = 10.0)),
                averages = listOf(
                    SubjectAverage("maths", 16.4, "T2", AverageSource.OFFICIAL),
                ),
            ),
        )

        val mathsRow = rows.first { it.subject.id == "maths" }
        assertEquals("16,4", mathsRow.display)
        assertEquals(AverageSource.OFFICIAL, mathsRow.source)
        assertEquals(1, mathsRow.gradeCount)

        // English has no remote average: it is computed locally.
        val englishRow = rows.first { it.subject.id == "anglais" }
        assertEquals(AverageSource.CALCULATED, englishRow.source)
    }

    @Test
    fun `counts excluded grades in the row total but not in the average`() {
        val rows = GetSubjectRowsUseCase().execute(
            snapshot(
                grades = listOf(
                    grade("a", value = 20.0),
                    grade("b", value = 4.0, counts = false),
                ),
            ),
        )

        val mathsRow = rows.first { it.subject.id == "maths" }
        assertEquals(2, mathsRow.gradeCount)
        assertEquals("20,0", mathsRow.display)
    }

    @Test
    fun `shows a placeholder for a subject without any grade`() {
        val rows = GetSubjectRowsUseCase().execute(snapshot(grades = emptyList()))
        assertEquals("—", rows.first { it.subject.id == "anglais" }.display)
        assertNull(rows.first { it.subject.id == "anglais" }.average)
    }

    @Test
    fun `returns an empty list when no snapshot is cached`() {
        assertEquals(emptyList<SubjectRow>(), GetSubjectRowsUseCase().execute(null))
        assertNull(CalculateGeneralAverageUseCase().execute(null))
    }

    @Test
    fun `builds the average summary with its display and month delta`() {
        val summary = CalculateGeneralAverageUseCase().execute(
            snapshot(
                grades = emptyList(),
                general = GeneralAverage(15.75, "T2", AverageSource.CALCULATED, 0L),
                history = listOf(
                    HistoryPoint("2026-01-05", 15.25),
                    HistoryPoint("2026-02-05", 15.25),
                ),
            ),
        )

        assertEquals("15,8", summary!!.display)
        assertEquals(AverageSource.CALCULATED, summary.source)
        assertEquals("+0,5", summary.deltaDisplay)
    }

    @Test
    fun `sorts grades newest first and filters by subject`() {
        val grades = listOf(
            grade("old", date = "2026-01-05"),
            grade("new", date = "2026-03-05"),
            grade("eng", subjectId = "anglais", date = "2026-02-05"),
        )
        val snapshot = snapshot(grades)

        assertEquals(
            listOf("new", "eng", "old"),
            GetGradesUseCase().execute(snapshot).map { it.id },
        )
        assertEquals(
            listOf("eng"),
            GetGradesUseCase().execute(snapshot, "anglais").map { it.id },
        )
    }

    @Test
    fun `maps the non significant flag from the connector`() {
        val significant = Mappers.mapGrade(PronoteGradeDto("g1", "maths", "DS"), 0)
        assertTrue(significant.countsTowardAverage)

        val excluded = Mappers.mapGrade(PronoteGradeDto("g2", "maths", "Test", nonSignificatif = true), 1)
        assertFalse(excluded.countsTowardAverage)
    }

    @Test
    fun `falls back to readable defaults for blank connector fields`() {
        val student = Mappers.mapStudent(PronoteStudentDto(prenom = " ", nom = null, classe = null))
        assertEquals("Élève", student.firstName)
        assertEquals("", student.lastName)

        val subject = Mappers.mapSubject(PronoteSubjectDto(id = null, nom = null, coefficient = 0.0), 3)
        assertEquals("Matière", subject.name)
        assertEquals("subject-3", subject.id)
        assertEquals(0.0001, subject.coefficient, 0.000001)

        val grade = Mappers.mapGrade(PronoteGradeDto(intitule = null, bareme = 0.0), 2)
        assertEquals("Évaluation", grade.title)
        assertEquals(0.0001, grade.maxValue, 0.000001)
    }
}