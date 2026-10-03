package com.pronote.clay.sync

import com.pronote.clay.core.Logger
import com.pronote.clay.core.PronoteResult
import com.pronote.clay.data.PronoteRepository
import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.SchoolSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SyncManager — the WorkManager equivalent.
 *
 * Guarantees:
 *  - never two concurrent syncs (mutex, mirroring a unique WorkManager job)
 *  - never sync when offline
 *  - skip when the last sync is recent, unless the caller forces it
 *  - detect new grades and average changes after every successful sync
 */
class SyncManager(
    private val repository: PronoteRepository,
    private val isOnline: () -> Boolean,
) {

    data class SyncResult(
        val ok: Boolean,
        val snapshot: SchoolSnapshot? = null,
        val newGrades: List<Grade> = emptyList(),
        val previousAverage: Double? = null,
        val nextAverage: Double? = null,
        val skipped: SkipReason? = null,
        val error: String? = null,
    )

    enum class SkipReason { OFFLINE, RECENT, BUSY }

    private val mutex = Mutex()
    private var lastSyncAt = 0L

    val isSyncing: Boolean get() = mutex.isLocked

    /**
     * Run a sync. `force` bypasses the "recent sync" guard (the "Actualiser"
     * button), but never bypasses the offline check or the mutex.
     */
    suspend fun sync(accountId: String, force: Boolean = false): SyncResult {
        if (!isOnline()) {
            Logger.info("Sync skipped: offline")
            return SyncResult(ok = false, skipped = SkipReason.OFFLINE)
        }

        if (mutex.isLocked) {
            Logger.info("Sync skipped: already running")
            return SyncResult(ok = false, skipped = SkipReason.BUSY)
        }

        if (!force && System.currentTimeMillis() - lastSyncAt < MIN_SYNC_INTERVAL_MS) {
            Logger.info("Sync skipped: recent")
            return SyncResult(ok = false, skipped = SkipReason.RECENT)
        }

        return mutex.withLock { run(accountId) }
    }

    private suspend fun run(accountId: String): SyncResult {
        val before = repository.getCachedSnapshot(accountId)
        val previousAverage = before?.generalAverage?.value

        Logger.info("Sync started")
        return when (val result = repository.sync(accountId)) {
            is PronoteResult.Failure -> {
                Logger.warn("Sync failed")
                SyncResult(
                    ok = false,
                    previousAverage = previousAverage,
                    nextAverage = previousAverage,
                    error = result.error.userMessage,
                )
            }
            is PronoteResult.Success -> {
                lastSyncAt = System.currentTimeMillis()
                SyncResult(
                    ok = true,
                    snapshot = result.value,
                    newGrades = detectNewGrades(before, result.value),
                    previousAverage = previousAverage,
                    nextAverage = result.value.generalAverage?.value,
                )
            }
        }
    }

    /** Called when a session is replaced so the next sync is never skipped. */
    fun reset() {
        lastSyncAt = 0L
    }

    companion object {
        /** Unique WorkManager work name. */
        const val PERIODIC_SYNC_WORK_NAME = "pronote_periodic_sync"

        /** Minimum delay between two automatic syncs. */
        const val MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L

        /** Compare the previous grade list with the new one. */
        fun detectNewGrades(before: SchoolSnapshot?, after: SchoolSnapshot): List<Grade> {
            if (before == null) return emptyList()
            val known = before.grades.map { it.id }.toSet()
            return after.grades.filter { it.id !in known }
        }
    }
}
