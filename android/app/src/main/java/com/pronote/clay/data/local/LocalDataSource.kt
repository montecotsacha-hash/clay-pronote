package com.pronote.clay.data.local

import android.content.Context
import com.pronote.clay.core.Logger
import com.pronote.clay.domain.PronoteSession
import com.pronote.clay.domain.SchoolSnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * LocalDataSource — the web/Room equivalent of the school cache.
 *
 * Every record is scoped to an account id. The UI is served from here instantly
 * (cache-first) and refreshed silently afterwards.
 *
 * Serialisation is hand-rolled JSON so the project stays dependency-light: the
 * data shapes are tiny and fully covered by the repository tests.
 */
class LocalDataSource(context: Context) {

    private val prefs = context.getSharedPreferences("pronote_school_cache", Context.MODE_PRIVATE)

    fun loadSnapshot(accountId: String): SchoolSnapshot? {
        val raw = prefs.getString(snapshotKey(accountId), null) ?: return null
        return try {
            decodeSnapshot(JSONObject(raw))
        } catch (error: Throwable) {
            Logger.warn("Local snapshot unreadable")
            null
        }
    }

    fun saveSnapshot(accountId: String, snapshot: SchoolSnapshot) {
        try {
            prefs.edit().putString(snapshotKey(accountId), encodeSnapshot(snapshot).toString()).apply()
        } catch (error: Throwable) {
            Logger.warn("Local snapshot write failed")
        }
    }

    fun clearSnapshot(accountId: String) {
        prefs.edit().remove(snapshotKey(accountId)).apply()
    }

    /** "Supprimer mes données" — wipes every trace of the account on this device. */
    fun clearAccount(accountId: String) {
        prefs.edit().remove(snapshotKey(accountId)).apply()
    }

    private fun snapshotKey(accountId: String) = "snapshot.$accountId"

    // ---- serialisation ----------------------------------------------------

    private fun encodeSnapshot(s: SchoolSnapshot): JSONObject = JSONObject().apply {
        put("student", JSONObject().apply {
            put("id", s.student.id)
            put("firstName", s.student.firstName)
            put("lastName", s.student.lastName)
            put("className", s.student.className)
            put("schoolName", s.student.schoolName)
        })
        put("subjects", JSONArray().apply {
            s.subjects.forEach { subject ->
                put(JSONObject().apply {
                    put("id", subject.id)
                    put("name", subject.name)
                    put("shortName", subject.shortName)
                    put("teacher", subject.teacher)
                    put("coefficient", subject.coefficient)
                    put("colorIndex", subject.colorIndex)
                })
            }
        })
        put("grades", JSONArray().apply {
            s.grades.forEach { grade ->
                put(JSONObject().apply {
                    put("id", grade.id)
                    put("subjectId", grade.subjectId)
                    put("title", grade.title)
                    put("value", grade.value)
                    put("maxValue", grade.maxValue)
                    put("coefficient", grade.coefficient)
                    put("date", grade.date)
                    put("comment", grade.comment)
                    put("counts", grade.countsTowardAverage)
                })
            }
        })
        put("averages", JSONArray().apply {
            s.subjectAverages.forEach { avg ->
                put(JSONObject().apply {
                    put("subjectId", avg.subjectId)
                    put("value", avg.value)
                    put("period", avg.period)
                    put("source", avg.source.name)
                })
            }
        })
        s.generalAverage?.let { avg ->
            put("general", JSONObject().apply {
                put("value", avg.value)
                put("period", avg.period)
                put("source", avg.source.name)
                put("updatedAt", avg.updatedAt)
            })
        }
        put("history", JSONArray().apply {
            s.history.forEach { point ->
                put(JSONObject().apply {
                    put("date", point.date)
                    put("value", point.value)
                })
            }
        })
        put("fetchedAt", s.fetchedAt)
    }

    private fun decodeSnapshot(json: JSONObject): SchoolSnapshot {
        val studentJson = json.getJSONObject("student")
        val subjectsJson = json.optJSONArray("subjects") ?: JSONArray()
        val gradesJson = json.optJSONArray("grades") ?: JSONArray()
        val averagesJson = json.optJSONArray("averages") ?: JSONArray()
        val historyJson = json.optJSONArray("history") ?: JSONArray()

        return SchoolSnapshot(
            student = com.pronote.clay.domain.Student(
                id = studentJson.getString("id"),
                firstName = studentJson.getString("firstName"),
                lastName = studentJson.getString("lastName"),
                className = studentJson.optString("className"),
                schoolName = studentJson.optString("schoolName"),
            ),
            subjects = (0 until subjectsJson.length()).map { i ->
                val o = subjectsJson.getJSONObject(i)
                com.pronote.clay.domain.Subject(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    shortName = o.optString("shortName"),
                    teacher = o.optString("teacher"),
                    coefficient = o.optDouble("coefficient", 1.0),
                    colorIndex = o.optInt("colorIndex", 0),
                )
            },
            grades = (0 until gradesJson.length()).map { i ->
                val o = gradesJson.getJSONObject(i)
                com.pronote.clay.domain.Grade(
                    id = o.getString("id"),
                    subjectId = o.getString("subjectId"),
                    title = o.optString("title"),
                    value = o.optDouble("value", 0.0),
                    maxValue = o.optDouble("maxValue", 20.0),
                    coefficient = o.optDouble("coefficient", 1.0),
                    date = o.optString("date"),
                    comment = o.optString("comment").takeIf { it.isNotBlank() && it != "null" },
                    countsTowardAverage = o.optBoolean("counts", true),
                )
            },
            subjectAverages = (0 until averagesJson.length()).map { i ->
                val o = averagesJson.getJSONObject(i)
                com.pronote.clay.domain.SubjectAverage(
                    subjectId = o.getString("subjectId"),
                    value = o.optDouble("value", 0.0),
                    period = o.optString("period"),
                    source = com.pronote.clay.domain.AverageSource.valueOf(
                        o.optString("source", "CALCULATED"),
                    ),
                )
            },
            generalAverage = if (json.has("general")) {
                val o = json.getJSONObject("general")
                com.pronote.clay.domain.GeneralAverage(
                    value = o.optDouble("value", 0.0),
                    period = o.optString("period"),
                    source = com.pronote.clay.domain.AverageSource.valueOf(
                        o.optString("source", "CALCULATED"),
                    ),
                    updatedAt = o.optLong("updatedAt"),
                )
            } else null,
            history = (0 until historyJson.length()).map { i ->
                val o = historyJson.getJSONObject(i)
                com.pronote.clay.domain.HistoryPoint(
                    date = o.getString("date"),
                    value = o.optDouble("value", 0.0),
                )
            },
            fetchedAt = json.optLong("fetchedAt"),
        )
    }

    // ---- session (non-secret part only) -----------------------------------

    fun saveSessionMeta(accountId: String, session: PronoteSession) {
        prefs.edit()
            .putString("session.$accountId.url", session.schoolUrl)
            .putString("session.$accountId.school", session.schoolName)
            .putLong("session.$accountId.expires", session.expiresAt)
            .apply()
    }

    fun sessionMeta(accountId: String): PronoteSession? {
        val url = prefs.getString("session.$accountId.url", null) ?: return null
        return PronoteSession(
            token = "", // the token itself only ever lives in the encrypted vault
            expiresAt = prefs.getLong("session.$accountId.expires", 0L),
            schoolUrl = url,
            schoolName = prefs.getString("session.$accountId.school", "") ?: "",
            connected = true,
        )
    }

    fun clearSessionMeta(accountId: String) {
        prefs.edit()
            .remove("session.$accountId.url")
            .remove("session.$accountId.school")
            .remove("session.$accountId.expires")
            .apply()
    }
}
