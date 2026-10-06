package com.pronote.clay.sync

import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import com.pronote.clay.data.PronoteRepository
import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.PronoteSession
import com.pronote.clay.domain.SchoolSnapshot
import com.pronote.clay.domain.Student
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JVM unit tests for the sync guards and the error propagation the UI relies on.
 */
class SyncManagerTest {

    private val student = Student("s1", "Alex", "Martin", "1re G2", "Lycée Jean Moulin")

    private fun snapshot(vararg grades: Grade) = SchoolSnapshot(
        student = student,
        subjects = emptyList(),
        grades = grades.toList(),
        subjectAverages = emptyList(),
        generalAverage = null,
        history = emptyList(),
        fetchedAt = 0L,
    )

    /** A date [days] ago, ISO formatted like Pronote grades (yyyy-MM-dd). */
    private fun isoDaysAgo(days: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - days * 86_400_000L))

    private fun grade(id: String, date: String = isoDaysAgo(0)) = Grade(
        id = id,
        subjectId = "maths",
        title = "Devoir",
        value = 15.0,
        maxValue = 20.0,
        coefficient = 1.0,
        date = date,
    )

    private class FakeRepository(
        private var current: SchoolSnapshot?,
        private val failure: PronoteError? = null,
    ) : PronoteRepository {
        var syncCount = 0

        override suspend fun login(
            accountId: String,
            schoolUrl: String,
            username: String,
            password: String,
        ): PronoteResult<PronoteSession> = PronoteResult.Failure(PronoteError.InvalidCredentials)

        override suspend fun refreshSession(accountId: String): PronoteSession? = null

        override fun getCachedSnapshot(accountId: String): SchoolSnapshot? = current

        override suspend fun sync(accountId: String): PronoteResult<SchoolSnapshot> {
            syncCount++
            failure?.let { return PronoteResult.Failure(it) }
            return PronoteResult.Success(current!!)
        }

        override suspend fun logout(accountId: String) = Unit

        override suspend fun deleteLocalData(accountId: String) = Unit
    }

    @Test
    fun `carries the real error hint to the UI`() = runBlocking {
        val manager = SyncManager(
            FakeRepository(null, PronoteError.SessionExpired),
        ) { true }

        val result = manager.sync("acct")

        assertFalse(result.ok)
        assertEquals("Ta session Pronote a expiré.", result.error)
        // The UI shows this hint; it used to be hardcoded to the network message.
        assertEquals(
            "Reconnecte-toi pour repartir sur des données à jour.",
            result.errorHint,
        )
    }

    @Test
    fun `never syncs while offline`() = runBlocking {
        val repository = FakeRepository(snapshot(grade("a")))
        val manager = SyncManager(repository) { false }

        val result = manager.sync("acct")

        assertEquals(SyncManager.SkipReason.OFFLINE, result.skipped)
        assertEquals(0, repository.syncCount)
    }

    @Test
    fun `skips a second sync that is too recent`() = runBlocking {
        val repository = FakeRepository(snapshot(grade("a")))
        val manager = SyncManager(repository) { true }

        assertTrue(manager.sync("acct").ok)
        val second = manager.sync("acct")

        assertEquals(SyncManager.SkipReason.RECENT, second.skipped)
        assertEquals(1, repository.syncCount)
    }

    @Test
    fun `force bypasses the recent guard`() = runBlocking {
        val repository = FakeRepository(snapshot(grade("a")))
        val manager = SyncManager(repository) { true }

        manager.sync("acct")
        val forced = manager.sync("acct", force = true)

        assertTrue(forced.ok)
        assertEquals(2, repository.syncCount)
    }

    @Test
    fun `detects the grades added since the previous sync`() {
        val before = snapshot(grade("a"))
        val after = snapshot(grade("a"), grade("b"))

        val detected = SyncManager.detectNewGrades(before, after)

        assertEquals(listOf("b"), detected.map { it.id })
    }

    @Test
    fun `does not report new grades on a first sync`() {
        assertEquals(emptyList<Grade>(), SyncManager.detectNewGrades(null, snapshot(grade("a"))))
    }

    @Test
    fun `never announces a grade older than the cutoff`() {
        val before = snapshot(grade("a"))
        val after = snapshot(
            grade("a"),
            grade("month-old", date = isoDaysAgo(SyncManager.MAX_NOTIFY_AGE_DAYS + 30)),
        )

        assertEquals(emptyList<Grade>(), SyncManager.detectNewGrades(before, after))
    }

    @Test
    fun `announces a grade that is recent enough`() {
        val before = snapshot(grade("a"))
        val after = snapshot(grade("a"), grade("fresh", date = isoDaysAgo(2)))

        assertEquals(listOf("fresh"), SyncManager.detectNewGrades(before, after).map { it.id })
    }

    @Test
    fun `announces the most recent grade first`() {
        val before = snapshot(grade("a"))
        val after = snapshot(
            grade("a"),
            grade("older", date = isoDaysAgo(4)),
            grade("newer", date = isoDaysAgo(1)),
        )

        assertEquals(
            listOf("newer", "older"),
            SyncManager.detectNewGrades(before, after).map { it.id },
        )
    }

    @Test
    fun `treats a wholesale id change as a new baseline, not as new grades`() {
        val before = snapshot(grade("a"), grade("b"), grade("c"), grade("d"), grade("e"))
        val after = snapshot(grade("x"), grade("y"), grade("z"), grade("w"), grade("v"))

        assertEquals(emptyList<Grade>(), SyncManager.detectNewGrades(before, after))
    }

    @Test
    fun `reports the average change around a successful sync`() = runBlocking {
        val repository = FakeRepository(snapshot(grade("a")))
        val manager = SyncManager(repository) { true }

        val result = manager.sync("acct")

        assertTrue(result.ok)
        assertNull(result.previousAverage)
        assertTrue(result.error == null && result.errorHint == null)
    }
}