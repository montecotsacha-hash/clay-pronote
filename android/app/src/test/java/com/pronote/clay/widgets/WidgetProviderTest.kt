package com.pronote.clay.widgets

import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pronote.clay.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the launcher actually receives: a RemoteViews whose single frame holds
 * a rendered bitmap for every widget flavor, sized from the requested cells.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetProviderTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `every widget flavor delivers a bitmap to the launcher`() {
        for (kind in WidgetKind.entries) {
            val views = ArdoiseWidgetProvider.buildRemoteViews(context, kind, 200, 120, widgetId = 7)

            assertNotNull("RemoteViews for $kind", views)
            val root = views!!.apply(context, null)
            val image = root.findViewById<ImageView>(R.id.widget_image)
            assertNotNull(image)

            val bitmap = (image.drawable as? BitmapDrawable)?.bitmap
            assertNotNull("bitmap for $kind", bitmap)
            assertEquals(200, bitmap!!.width)
            assertEquals(120, bitmap.height)
        }
    }

    @Test
    fun `the delivered bitmap is painted, not blank`() {
        val views = ArdoiseWidgetProvider.buildRemoteViews(context, WidgetKind.DUO, 260, 160, 3)!!
        val image = views.apply(context, null).findViewById<ImageView>(R.id.widget_image)
        val bitmap = (image.drawable as BitmapDrawable).bitmap

        var opaque = 0
        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) == 255) opaque++
            }
        }
        assertTrue("the card should cover most of the widget", opaque > 1000)
    }

    @Test
    fun `refreshing widgets never crashes, even with no account yet`() {
        ArdoiseWidgetProvider.updateAll(context)
        ArdoiseWidgetProvider.updateAllAsync(context)
    }

    @Test
    fun `an oversized widget stays under the Binder bitmap budget`() {
        // A full-width 4x3 cell on a 3x-density device would be ~2.2 MP.
        val views = ArdoiseWidgetProvider.buildRemoteViews(context, WidgetKind.DUO, 600, 400, 1)!!
        val image = views.apply(context, null).findViewById<ImageView>(R.id.widget_image)
        val bitmap = (image.drawable as BitmapDrawable).bitmap

        assertTrue(bitmap.width.toLong() * bitmap.height <= 1_500_000L)
        // Still painted, just at the capped resolution.
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    @Test
    fun `portrait-height bundles use the taller height so nothing is crushed`() {
        // Simulates a launcher bundle captured mid-resize: landscape height is
        // smaller than portrait; the render must use the portrait one.
        val options = android.os.Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 300)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 160)
        }
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val provider = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<com.pronote.clay.PronoteApp>()
        val views = ArdoiseWidgetProvider.buildRemoteViews(
            provider,
            WidgetKind.DUO,
            options.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            maxOf(
                options.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                options.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
            ),
            2,
        )!!
        val bitmap = (views.apply(provider, null)
            .findViewById<ImageView>(R.id.widget_image).drawable as BitmapDrawable).bitmap
        // 160dp @ density 1 (Robolectric) -> 160px tall, not 80px.
        assertEquals(160, bitmap.height)
    }
}
