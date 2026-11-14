package com.pronote.clay

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
 *
 * Cadence : une heure, avec la fenêtre de flexibilité de quinze minutes de
 * WorkManager. Six heures était trop espacé — une note publiée le matin
 * n'alertait que le soir, et sur les téléphones qui gèlent agressivement les
 * tâches de fond la fenêtre pouvait être manquée entièrement. Une passe
 * immédiate au démarrage amorce la chaîne.
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
        scheduleStartupSync()
    }

    private val syncConstraints: Constraints
        get() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS,
            SYNC_FLEX_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(syncConstraints)
            // Une erreur passagère (réseau captif, serveur en maintenance) est
            // retentée sans marteler l'établissement.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        runCatching {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                SyncManager.PERIODIC_SYNC_WORK_NAME,
                // UPDATE : une nouvelle cadence remplace l'ancienne tâche au
                // lieu de la laisser tourner avec des réglages périmés.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    /**
     * Une passe immédiate à chaque démarrage : elle amorce la session et fait
     * apparaître la première alerte sans attendre le prochain tick périodique.
     */
    private fun scheduleStartupSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(syncConstraints)
            .build()

        runCatching {
            WorkManager.getInstance(this).enqueueUniqueWork(
                SyncManager.STARTUP_SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    private companion object {
        const val SYNC_INTERVAL_HOURS = 1L
        const val SYNC_FLEX_MINUTES = 15L
    }
}