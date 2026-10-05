package com.pronote.clay.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pronote.clay.PronoteApp
import com.pronote.clay.core.Logger
import com.pronote.clay.notifications.LocalNotifications

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
        if (!container.preferences.load().autoSync) {
            Logger.info("Sync skipped: automatic sync disabled")
            return androidx.work.ListenableWorker.Result.success()
        }

        val accountId = container.activeAccountId()
            ?: return androidx.work.ListenableWorker.Result.success()

        if (!container.networkMonitor.isOnline()) {
            Logger.info("Sync skipped: offline")
            return androidx.work.ListenableWorker.Result.success()
        }

        if (container.refreshSession.execute(accountId) == null) {
            Logger.warn("Background sync skipped: session could not be restored")
            return androidx.work.ListenableWorker.Result.retry()
        }

        val result = container.syncManager.sync(accountId)
        if (result.ok) {
            val preferences = container.preferences.load()
            val snapshot = result.snapshot
            if (snapshot != null && preferences.notifyNewGrades && result.newGrades.isNotEmpty()) {
                val grade = result.newGrades.first()
                val subject = snapshot.subjects.find { it.id == grade.subjectId }
                LocalNotifications.notify(
                    applicationContext,
                    "Nouvelle note",
                    LocalNotifications.newGradeNotification(grade, subject),
                )
            } else if (
                preferences.notifyAverageChange &&
                result.previousAverage != null && result.nextAverage != null &&
                kotlin.math.abs(result.nextAverage - result.previousAverage) >= 0.05
            ) {
                LocalNotifications.notify(
                    applicationContext,
                    "Ta moyenne a changé",
                    LocalNotifications.averageChangeNotification(
                        result.nextAverage,
                        result.previousAverage,
                    ),
                )
            }
        }

        return if (result.ok || result.skipped != null) {
            androidx.work.ListenableWorker.Result.success()
        } else {
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}
