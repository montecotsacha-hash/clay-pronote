package com.pronote.clay

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pronote.clay.notifications.LocalNotifications
import com.pronote.clay.sync.SyncManager
import com.pronote.clay.sync.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * PronoteApp — holds the composition root and schedules the periodic sync.
 *
 * The sync is registered as unique work named
 * [SyncManager.PERIODIC_SYNC_WORK_NAME] and only runs with a network
 * connection, so it never duplicates and never wastes battery offline.
 */
class PronoteApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.networkMonitor.start()
        LocalNotifications.ensureChannel(this)
        schedulePeriodicSync()
    }

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            6,
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        runCatching {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                SyncManager.PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}