package com.pronote.clay.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.pronote.clay.MainActivity
import com.pronote.clay.PronoteApp
import com.pronote.clay.R
import com.pronote.clay.core.Logger

/**
 * ArdoiseWidgetProvider — common plumbing for the four home-screen widgets.
 *
 * Widgets cannot host Compose: the card is drawn by [WidgetArt] into a bitmap
 * and pushed through a single-frame RemoteViews. Every widget opens the app
 * when tapped, and is re-rendered whenever a snapshot is saved, when the user
 * resizes it, and on the launcher's own periodic update.
 */
abstract class ArdoiseWidgetProvider : AppWidgetProvider() {

    protected abstract val kind: WidgetKind

    /** Used until the launcher reports a real size (first update). */
    protected abstract val fallbackWidthDp: Int
    protected abstract val fallbackHeightDp: Int

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderIds(context, appWidgetManager, appWidgetIds, kind, fallbackWidthDp, fallbackHeightDp)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        options: android.os.Bundle,
    ) {
        // Resized by the user: re-render that widget at the new aspect ratio.
        renderIds(
            context, appWidgetManager, intArrayOf(appWidgetId),
            kind, fallbackWidthDp, fallbackHeightDp,
        )
    }

    protected fun renderIds(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        kind: WidgetKind,
        fallbackWidthDp: Int,
        fallbackHeightDp: Int,
    ) {
        for (id in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(id)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                .takeIf { it > 0 } ?: fallbackWidthDp
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                .takeIf { it > 0 } ?: fallbackHeightDp
            val views = buildRemoteViews(context, kind, widthDp, heightDp, id) ?: continue
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {

        /** Called after every snapshot save so widgets never show stale data. */
        fun updateAllAsync(context: Context) {
            val appContext = context.applicationContext
            Thread {
                runCatching { updateAll(appContext) }
                    .onFailure { Logger.warn("Widget update failed: ${it.javaClass.simpleName}") }
            }.start()
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val providers = listOf(
                AverageWidgetProvider::class.java,
                CurveWidgetProvider::class.java,
                DuoWidgetProvider::class.java,
                GradeWidgetProvider::class.java,
            )
            for (providerClass in providers) {
                val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
                if (ids == null || ids.isEmpty()) continue
                runCatching {
                    val provider = providerClass.getDeclaredConstructor().newInstance()
                    (provider as ArdoiseWidgetProvider).onUpdate(context, manager, ids)
                }.onFailure { Logger.warn("Widget refresh failed: ${it.javaClass.simpleName}") }
            }
        }

        /**
         * Builds the RemoteViews for one widget instance. Public so the tests
         * can assert what the launcher actually receives.
         */
        fun buildRemoteViews(
            context: Context,
            kind: WidgetKind,
            widthDp: Int,
            heightDp: Int,
            widgetId: Int = 0,
        ): RemoteViews? {
            val container = (context.applicationContext as? PronoteApp)?.container ?: return null

            val accountId = container.activeAccountId()
            val snapshot = accountId?.let { container.getCachedData.execute(it) }
            val average = container.calculateGeneralAverage.execute(snapshot)
            val preferences = container.preferences.load()

            val dark = when (preferences.darkTheme) {
                true -> true
                false -> false
                null -> {
                    val night = context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                    night == Configuration.UI_MODE_NIGHT_YES
                }
            }

            val density = context.resources.displayMetrics.density
            val bitmap = WidgetArt.render(
                kind = kind,
                widthPx = (widthDp * density).toInt(),
                heightPx = (heightDp * density).toInt(),
                density = density,
                dark = dark,
                snapshot = snapshot,
                average = average,
            )

            return RemoteViews(context.packageName, R.layout.widget_frame).apply {
                setImageViewBitmap(R.id.widget_image, bitmap)
                setOnClickPendingIntent(R.id.widget_image, openAppIntent(context, widgetId))
            }
        }

        /** Tapping anywhere on the card opens the app (like the notifications). */
        private fun openAppIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context,
                widgetId + REQUEST_CODE_BASE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** Distinct request code per widget so each keeps its own PendingIntent. */
        private const val REQUEST_CODE_BASE = 5100
    }
}

/** Moyenne générale, seule. */
class AverageWidgetProvider : ArdoiseWidgetProvider() {
    override val kind = WidgetKind.AVERAGE
    override val fallbackWidthDp = 180
    override val fallbackHeightDp = 110
}

/** Courbe d'évolution, seule. */
class CurveWidgetProvider : ArdoiseWidgetProvider() {
    override val kind = WidgetKind.CURVE
    override val fallbackWidthDp = 250
    override val fallbackHeightDp = 110
}

/** Moyenne + courbe ensemble. */
class DuoWidgetProvider : ArdoiseWidgetProvider() {
    override val kind = WidgetKind.DUO
    override val fallbackWidthDp = 250
    override val fallbackHeightDp = 160
}

/** Dernière note reçue. */
class GradeWidgetProvider : ArdoiseWidgetProvider() {
    override val kind = WidgetKind.GRADE
    override val fallbackWidthDp = 180
    override val fallbackHeightDp = 160
}
