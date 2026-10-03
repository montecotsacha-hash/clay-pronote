package com.pronote.clay.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pronote.clay.core.AverageCalculator
import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.Subject

/**
 * Local notifications — generated on-device after a sync.
 *
 * No school data is ever sent to a third-party service just to produce a
 * notification, and nothing here runs when the user disabled the category.
 */
object LocalNotifications {

    private const val CHANNEL_ID = "pronote_updates"
    private const val NOTIFICATION_ID = 4201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Suivi scolaire",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Nouvelles notes et évolution de la moyenne générale."
        }
        manager.createNotificationChannel(channel)
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notify(context: Context, title: String, body: String) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun newGradeNotification(grade: Grade, subject: Subject?): String {
        val name = subject?.name?.lowercase() ?: "une matière"
        return "Une nouvelle note de ${AverageCalculator.formatGrade(grade.value, grade.maxValue)} " +
            "en $name vient d'être ajoutée."
    }

    fun averageChangeNotification(next: Double, previous: Double): String {
        val direction = if (next >= previous) "est maintenant de" else "est passée à"
        return "Ta moyenne générale $direction ${AverageCalculator.formatAverage(next)}/20."
    }
}
