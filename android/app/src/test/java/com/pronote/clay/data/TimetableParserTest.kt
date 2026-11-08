package com.pronote.clay.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pronote.clay.data.remote.parseTimetableLessons
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The timetable parser reads the REAL `PageEmploiDuTemps` wire format.
 *
 * These tests exist because the previous implementation looked for field names
 * that do not exist in the protocol (`dateDuCour`, `ListeProfesseurs`…) and
 * silently produced an empty list — the "Emploi du temps indisponible" the user
 * saw. Robolectric is used so `org.json` is the real Android implementation
 * rather than the stubbed `android.jar`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimetableParserTest {

    private fun course(
        start: Any,
        end: Any? = null,
        cancelled: Boolean = false,
        statut: String? = null,
        memo: String? = null,
        contents: JSONArray? = null,
    ): JSONObject = JSONObject().apply {
        put("DateDuCours", JSONObject().put("V", start))
        end?.let { put("DateDuCoursFin", JSONObject().put("V", it)) }
        put("estAnnule", cancelled)
        statut?.let { put("Statut", it) }
        memo?.let { put("memo", it) }
        contents?.let { put("ListeContenus", JSONObject().put("V", it)) }
    }

    private fun resource(type: Int, label: String, id: String? = null): JSONObject =
        JSONObject().apply {
            id?.let { put("N", it) }
            put("G", type)
            put("L", label)
        }

    @Test
    fun `subject, teacher, room and group come out of ListeContenus`() {
        val response = JSONObject().put(
            "ListeCours",
            JSONArray().put(
                course(
                    start = "05/10/2026 08:00:00",
                    end = "05/10/2026 10:00:00",
                    contents = JSONArray()
                        .put(resource(16, "MATHS SPECIFIQUES", id = "1"))
                        .put(resource(3, "M. DUPONT"))
                        .put(resource(17, "B204"))
                        .put(resource(2, "Groupe A")),
                ),
            ),
        )

        val lessons = parseTimetableLessons(response)

        assertEquals(1, lessons.size)
        val lesson = lessons.single()
        assertEquals("MATHS SPECIFIQUES", lesson.nomMatiere)
        assertEquals("1", lesson.matiere)
        assertEquals("M. DUPONT", lesson.professeur)
        assertEquals("B204", lesson.salle)
        assertEquals("Groupe A", lesson.groupe)
        assertEquals("2026-10-05T08:00", lesson.debut)
        assertEquals("2026-10-05T10:00", lesson.fin)
        assertFalse(lesson.annule)
    }

    @Test
    fun `dates are read whether wrapped in V, as a string or as an array`() {
        val response = JSONObject().put(
            "ListeCours",
            JSONArray()
                // dd/MM/yyyy HH:mm, enveloppé dans V (forme habituelle)
                .put(course("05/10/2026 08:00:00", "05/10/2026 09:00:00"))
                // tableau [année, mois, jour, heure, minute]
                .put(
                    course(
                        start = JSONArray(listOf(2026, 10, 6, 14, 0)),
                        end = JSONArray(listOf(2026, 10, 6, 15, 30)),
                    ),
                ),
        )

        val lessons = parseTimetableLessons(response)

        assertEquals(listOf("2026-10-05T08:00", "2026-10-06T14:00"), lessons.map { it.debut })
        assertEquals(listOf("2026-10-05T09:00", "2026-10-06T15:30"), lessons.map { it.fin })
    }

    @Test
    fun `cancelled courses are flagged from estAnnule and from the status`() {
        val response = JSONObject().put(
            "ListeCours",
            JSONArray()
                .put(course("05/10/2026 08:00:00", cancelled = true))
                .put(course("05/10/2026 09:00:00", statut = "Cours annulé"))
                .put(course("05/10/2026 10:00:00", statut = "Normal")),
        )

        val lessons = parseTimetableLessons(response)

        assertEquals(listOf(true, true, false), lessons.map { it.annule })
    }

    @Test
    fun `the memo is kept and the status is surfaced only when meaningful`() {
        val response = JSONObject().put(
            "ListeCours",
            JSONArray()
                .put(course("05/10/2026 08:00:00", memo = "Contrôle", statut = "Devoir"))
                .put(course("05/10/2026 09:00:00", statut = "Normal")),
        )

        val lessons = parseTimetableLessons(response)

        assertEquals("Contrôle — Devoir", lessons[0].memo)
        assertNull(lessons[1].memo)
    }

    @Test
    fun `ListeCours is accepted directly or wrapped in V`() {
        val cours = course("05/10/2026 08:00:00", "05/10/2026 09:00:00")
        val direct = JSONObject().put("ListeCours", JSONArray().put(cours))
        val wrapped = JSONObject().put("ListeCours", JSONObject().put("V", JSONArray().put(cours)))

        assertEquals(1, parseTimetableLessons(direct).size)
        assertEquals(1, parseTimetableLessons(wrapped).size)
    }

    @Test
    fun `a missing end time falls back to the establishment hour grid`() {
        val response = JSONObject().put(
            "ListeCours",
            JSONArray().put(
                course("05/10/2026 08:00:00").apply {
                    put("place", 1)
                    put("duree", 1)
                },
            ),
        )
        val grid = listOf(8 to 0, 9 to 0, 10 to 0, 11 to 0)

        val lesson = parseTimetableLessons(response, grid).single()

        assertEquals("2026-10-05T08:00", lesson.debut)
        assertEquals("2026-10-05T09:00", lesson.fin)
    }

    @Test
    fun `a course without any parsable date is skipped instead of crashing`() {
        val response = JSONObject().put(
            "ListeCours",
            JSONArray()
                .put(JSONObject().put("DateDuCours", JSONObject().put("V", "pas une date")))
                .put(course("05/10/2026 08:00:00")),
        )

        val lessons = parseTimetableLessons(response)

        assertEquals(1, lessons.size)
        assertTrue(lessons.single().debut!!.startsWith("2026-10-05"))
    }
}
