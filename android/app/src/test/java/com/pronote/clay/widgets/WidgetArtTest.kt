package com.pronote.clay.widgets

import android.graphics.Color
import com.pronote.clay.domain.AverageSource
import com.pronote.clay.domain.AverageSummary
import com.pronote.clay.domain.GeneralAverage
import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.HistoryPoint
import com.pronote.clay.domain.SchoolSnapshot
import com.pronote.clay.domain.Student
import com.pronote.clay.domain.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Widget rendering is pure Android graphics, so it runs on the real Skia
 * pipeline under Robolectric: these tests check the card is actually drawn
 * (not a blank bitmap), that its corners stay transparent, that the light and
 * dark palettes differ, and that the curve and the number are painted.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetArtTest {

    private val average = AverageSummary(
        value = 14.6,
        source = AverageSource.OFFICIAL,
        display = "14,6",
        delta = 0.3,
        deltaDisplay = "+0,3",
    )

    private fun snapshot(): SchoolSnapshot = SchoolSnapshot(
        student = Student("s1", "eleve", "Martin", "1 G2", "Lycée Joubert"),
        subjects = listOf(Subject("m", "Maths", "MAT", "Mme X", 2.0, 0)),
        grades = listOf(Grade("g1", "m", "Devoir", 15.0, 20.0, 1.0, "2026-10-02")),
        subjectAverages = emptyList(),
        generalAverage = GeneralAverage(14.6, "T1", AverageSource.OFFICIAL, 0L),
        history = listOf(12.0, 12.6, 13.1, 13.4, 14.0, 14.6).map { HistoryPoint("2026-10-01", it) },
        fetchedAt = 0L,
    )

    private fun render(
        kind: WidgetKind,
        dark: Boolean = false,
        withData: Boolean = true,
    ) = WidgetArt.render(
        kind = kind,
        widthPx = 400,
        heightPx = 220,
        density = 2f,
        dark = dark,
        snapshot = if (withData) snapshot() else null,
        average = if (withData) average else null,
    )

    /** Counts pixels whose RGB is within [tolerance] (sum of channel gaps) of [target]. */
    private fun countNear(bitmap: android.graphics.Bitmap, target: Int, tolerance: Int): Int {
        var count = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) == 0) continue
                val distance = Math.abs(Color.red(pixel) - Color.red(target)) +
                    Math.abs(Color.green(pixel) - Color.green(target)) +
                    Math.abs(Color.blue(pixel) - Color.blue(target))
                if (distance <= tolerance) count++
            }
        }
        return count
    }

    @Test
    fun `the card fills the widget but leaves transparent corners`() {
        val bitmap = render(WidgetKind.AVERAGE)

        assertEquals(400, bitmap.width)
        assertEquals(220, bitmap.height)
        // Outside the rounded card: the wallpaper must show through.
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(399, 219)))
        // Inside: fully painted.
        assertEquals(255, Color.alpha(bitmap.getPixel(200, 110)))
    }

    @Test
    fun `light and dark palettes are actually different`() {
        val lightTop = render(WidgetKind.AVERAGE, dark = false).getPixel(200, 4)
        val darkTop = render(WidgetKind.AVERAGE, dark = true).getPixel(200, 4)

        assertTrue("light card expected", Color.red(lightTop) > 200)
        assertTrue("dark card expected", Color.red(darkTop) < 120)
        assertTrue(Color.alpha(lightTop) == 255 && Color.alpha(darkTop) == 255)
    }

    @Test
    fun `the average widget paints its big number`() {
        val bitmap = render(WidgetKind.AVERAGE)
        // Near-black type of the value, on the light card.
        assertTrue(countNear(bitmap, 0xFF2A2240.toInt(), 90) > 30)
    }

    @Test
    fun `the curve widget paints the accent line`() {
        val bitmap = render(WidgetKind.CURVE)
        // Brand purple of the sparkline (plus its glow and area).
        assertTrue(countNear(bitmap, 0xFF9B7BE0.toInt(), 90) > 30)
    }

    @Test
    fun `the duo widget paints both the number and the curve`() {
        val bitmap = render(WidgetKind.DUO)
        assertTrue(countNear(bitmap, 0xFF2A2240.toInt(), 90) > 30)
        assertTrue(countNear(bitmap, 0xFF9B7BE0.toInt(), 90) > 30)
    }

    @Test
    fun `the grade widget shows its subject accent dot`() {
        val bitmap = render(WidgetKind.GRADE)
        // Subject colorIndex 0 = the same purple as the theme accent.
        assertTrue(countNear(bitmap, 0xFF9B7BE0.toInt(), 90) > 10)
    }

    @Test
    fun `an empty state still renders a clean card`() {
        for (kind in WidgetKind.entries) {
            val bitmap = render(kind, withData = false)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(255, Color.alpha(bitmap.getPixel(200, 110)))
            // Placeholder dash + hint text are painted in the muted color.
            assertTrue(countNear(bitmap, 0xFF6B5F87.toInt(), 120) > 10)
        }
    }

    @Test
    fun `tiny widgets are still rendered at the requested size`() {
        val bitmap = WidgetArt.render(
            kind = WidgetKind.AVERAGE,
            widthPx = 240,
            heightPx = 96,
            density = 3f,
            dark = false,
            snapshot = snapshot(),
            average = average,
        )
        assertEquals(240, bitmap.width)
        assertEquals(96, bitmap.height)
        assertEquals(255, Color.alpha(bitmap.getPixel(120, 48)))
    }

    @Test
    fun `a narrow average widget shrinks its type instead of clipping it`() {
        // ~2x1 cells on a real launcher, in px at density 2.
        val bitmap = WidgetArt.render(
            kind = WidgetKind.AVERAGE,
            widthPx = 300,
            heightPx = 104,
            density = 2f,
            dark = false,
            snapshot = snapshot(),
            average = average,
        )
        // The big number is still painted...
        assertTrue(countNear(bitmap, 0xFF2A2240.toInt(), 90) > 10)
        // ...and none of its ink touches the right edge (no truncated digits).
        for (y in 0 until bitmap.height) {
            val pixel = bitmap.getPixel(bitmap.width - 2, y)
            assertTrue(
                "ink must not reach the edge",
                Color.alpha(pixel) == 0 ||
                    Math.abs(Color.red(pixel) - 0x2A) +
                    Math.abs(Color.green(pixel) - 0x22) +
                    Math.abs(Color.blue(pixel) - 0x40) > 90,
            )
        }
    }

    @Test
    fun `a duo widget too short for both blocks degrades to the curve`() {
        val bitmap = WidgetArt.render(
            kind = WidgetKind.DUO,
            widthPx = 320,
            heightPx = 120,
            density = 2f,
            dark = false,
            snapshot = snapshot(),
            average = average,
        )
        // Curve painted, number and eyebrow absent: nothing crushed.
        assertTrue(countNear(bitmap, 0xFF9B7BE0.toInt(), 90) > 20)
        assertEquals(0, countNear(bitmap, 0xFF2A2240.toInt(), 90))
    }

    @Test
    fun `the curve widget still plots when the snapshot carries no history`() {
        val withoutHistory = snapshot().copy(history = emptyList())
        val bitmap = WidgetArt.render(
            kind = WidgetKind.CURVE,
            widthPx = 480,
            heightPx = 200,
            density = 2f,
            dark = false,
            snapshot = withoutHistory,
            average = average,
        )
        // The series is rebuilt from the grades, so the line is there.
        assertTrue(countNear(bitmap, 0xFF9B7BE0.toInt(), 90) > 30)
    }
}
