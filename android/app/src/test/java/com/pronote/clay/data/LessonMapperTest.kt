package com.pronote.clay.data

import com.pronote.clay.data.remote.PronoteLessonDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mappers.mapLesson — the DTO-to-domain bridge of the timetable feature. */
class LessonMapperTest {

    @Test
    fun `maps a full lesson with teacher room and group`() {
        val lesson = Mappers.mapLesson(
            PronoteLessonDto(
                "maths", "Mathématiques", "Mme Dubois", "B204", "1 G2",
                debut = "2026-10-05T08:00:00", fin = "2026-10-05T09:00:00",
            ),
        )!!

        assertEquals("maths", lesson.subjectId)
        assertEquals("Mathématiques", lesson.subjectName)
        assertEquals("Mme Dubois", lesson.teacher)
        assertEquals("B204", lesson.room)
        assertEquals("1 G2", lesson.group)
        assertEquals("2026-10-05T08:00:00", lesson.startIso)
        assertEquals("2026-10-05T09:00:00", lesson.endIso)
        assertFalse(lesson.cancelled)
        assertNull(lesson.memo)
    }

    @Test
    fun `returns null when the start timestamp is missing or too short`() {
        assertNull(Mappers.mapLesson(PronoteLessonDto("maths", "Maths", null, null, null, debut = null, fin = null)))
        assertNull(Mappers.mapLesson(PronoteLessonDto("maths", "Maths", null, null, null, debut = "2026-10-05", fin = null)))
    }

    @Test
    fun `falls back the end to the start when the end is missing`() {
        val lesson = Mappers.mapLesson(
            PronoteLessonDto("maths", "Maths", null, null, null, debut = "2026-10-05T08:00:00", fin = null),
        )!!
        assertEquals("2026-10-05T08:00:00", lesson.endIso)
    }

    @Test
    fun `names the course from the long name then the short code then the default`() {
        val full = Mappers.mapLesson(PronoteLessonDto("maths", "Mathématiques", null, null, null, debut = "2026-10-05T08:00:00", fin = null))!!
        val short = Mappers.mapLesson(PronoteLessonDto("maths", null, null, null, null, debut = "2026-10-05T08:00:00", fin = null))!!
        val blank = Mappers.mapLesson(PronoteLessonDto("  ", "  ", null, null, null, debut = "2026-10-05T08:00:00", fin = null))!!
        assertEquals("Mathématiques", full.subjectName)
        assertEquals("maths", short.subjectName)
        assertEquals("Cours", blank.subjectName)
    }

    @Test
    fun `keeps the cancelled flag and trims the memo`() {
        val lesson = Mappers.mapLesson(
            PronoteLessonDto("ses", "SES", null, null, null, debut = "2026-10-06T08:00:00", fin = null, annule = true, memo = "  Cours annulé  "),
        )!!
        assertTrue(lesson.cancelled)
        assertEquals("Cours annulé", lesson.memo)
    }

    @Test
    fun `drops blank optional fields instead of showing empty chips`() {
        val lesson = Mappers.mapLesson(
            PronoteLessonDto("maths", "Maths", "  ", "  ", "  ", debut = "2026-10-05T08:00:00", fin = null),
        )!!
        assertNull(lesson.teacher)
        assertNull(lesson.room)
        assertNull(lesson.group)
    }
}
