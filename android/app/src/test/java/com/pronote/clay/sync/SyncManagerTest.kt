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

    private fun grade(id: String) = Grade(
        id = id,
        subjectId = "maths",
        title = "Devoir",
        value = 15.0,
        maxValue = 20.0,
        coefficient = 1.0,
        date = "2026-01-10",
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
    fun `reports the average change around a successful sync`() = runBlocking {
        val repository = FakeRepository(snapshot(grade("a")))
        val manager = SyncManager(repository) { true }

        val result = manager.sync("acct")

        assertTrue(result.ok)
        assertNull(result.previousAverage)
        assertTrue(result.error == null && result.errorHint == null)
    }
}