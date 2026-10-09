package com.pronote.clay.data

import com.pronote.clay.data.remote.PronoteGradeDto
import com.pronote.clay.data.remote.PronoteLessonDto
import com.pronote.clay.data.remote.PronoteStudentDto
import com.pronote.clay.data.remote.PronoteSubjectAverageDto
import com.pronote.clay.data.remote.PronoteSubjectDto
import com.pronote.clay.domain.AverageSource
import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.Student
import com.pronote.clay.domain.Subject
import com.pronote.clay.domain.SubjectAverage
import com.pronote.clay.domain.TimetableLesson

/**
 * GradeMapper / AverageMapper — the only place DTOs become domain models.
 *
 * PronoteGradeDto -> Grade -> (stored) -> GradeUiModel
 */
object Mappers {

    fun mapStudent(dto: PronoteStudentDto): Student = Student(
        id = dto.id ?: "student",
        firstName = dto.prenom?.trim()?.takeUnless { it.isEmpty() } ?: "Élève",
        lastName = dto.nom?.trim().orEmpty(),
        className = dto.classe?.trim().orEmpty(),
        schoolName = dto.etablissement?.trim().orEmpty(),
    )

    fun mapSubject(dto: PronoteSubjectDto, index: Int): Subject {
        val name = dto.nom?.trim()?.takeUnless { it.isEmpty() } ?: "Matière"
        val short = dto.nomCourt?.trim()?.takeUnless { it.isEmpty() } ?: name.take(3)

        return Subject(
            id = dto.id ?: "subject-$index",
            name = name,
            shortName = short,
            teacher = dto.professeur?.trim().orEmpty(),
            coefficient = (dto.coefficient ?: 1.0).coerceAtLeast(0.0001),
            colorIndex = index % 6,
        )
    }

    fun mapGrade(dto: PronoteGradeDto, index: Int): Grade = Grade(
        id = dto.id ?: "grade-$index",
        subjectId = dto.matiere ?: "unknown",
        title = dto.intitule?.trim()?.takeUnless { it.isEmpty() } ?: "Évaluation",
        value = dto.note,
        maxValue = dto.bareme.coerceAtLeast(0.0001),
        coefficient = dto.coefficient.coerceAtLeast(0.0001),
        date = dto.date ?: "",
        comment = dto.commentaire?.trim()?.takeUnless { it.isEmpty() },
        countsTowardAverage = !dto.nonSignificatif,
    )

    fun mapSubjectAverage(dto: PronoteSubjectAverageDto): SubjectAverage = SubjectAverage(
        subjectId = dto.matiere ?: "unknown",
        value = dto.moyenne,
        period = dto.periode ?: "T2",
        source = AverageSource.OFFICIAL,
    )

    fun mapLesson(dto: PronoteLessonDto): TimetableLesson? {
        val start = dto.debut?.takeIf { it.length >= 16 } ?: return null
        val end = dto.fin?.takeIf { it.length >= 16 } ?: start
        val name = dto.nomMatiere?.trim()?.takeUnless { it.isEmpty() }
            ?: dto.matiere?.trim()?.takeUnless { it.isEmpty() }
            ?: "Cours"
        return TimetableLesson(
            subjectId = dto.matiere?.takeIf { it.isNotBlank() },
            subjectName = name,
            teacher = dto.professeur?.trim()?.takeUnless { it.isEmpty() },
            room = dto.salle?.trim()?.takeUnless { it.isEmpty() },
            group = dto.groupe?.trim()?.takeUnless { it.isEmpty() },
            startIso = start,
            endIso = end,
            cancelled = dto.annule,
            memo = dto.memo?.trim()?.takeUnless { it.isEmpty() },
        )
    }
}
