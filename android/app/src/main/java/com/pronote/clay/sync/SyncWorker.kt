package com.pronote.clay.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pronote.clay.PronoteApp
import com.pronote.clay.core.Logger

/**
 * SyncWorker — periodic background synchronisation.
 *
 * Registered as unique work under [SyncManager.PERIODIC_SYNC_WORK_NAME] so the
 * system can never run two syncs at once. It exits early when offline.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val container = (applicationContext as PronoteApp).container
        val accountId = container.activeAccountId()
            ?: return androidx.work.ListenableWorker.Result.success()

        if (!container.networkMonitor.isOnline()) {
            Logger.info("Sync skipped: offline")
            return androidx.work.ListenableWorker.Result.success()
        }

        val result = container.syncManager.sync(accountId)
        return if (result.ok || result.skipped != null) {
            androidx.work.ListenableWorker.Result.success()
        } else {
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}
