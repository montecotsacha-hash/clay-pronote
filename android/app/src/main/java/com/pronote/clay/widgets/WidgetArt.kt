package com.pronote.clay.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.pronote.clay.core.AverageCalculator
import com.pronote.clay.domain.AverageSource
import com.pronote.clay.domain.AverageSummary
import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.SchoolSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** The four widget flavors the launcher offers. */
enum class WidgetKind { AVERAGE, CURVE, DUO, GRADE }

/**
 * WidgetArt — hand-drawn art for the home-screen widgets.
 *
 * Widgets cannot host Compose, so every pixel is painted here: a frosted clay
 * card (gradient + top sheen + accent bloom + hairline), a big number in the
 * app's typography and a smooth average curve. Colors follow the app palette
 * (see `ui/theme/Color.kt`) in both light and dark.
 *
 * Everything is laid out from the actual pixel size, so the same renderer
 * looks right on a 2x1 cell and on a 4x2 one.
 */
object WidgetArt {

    /** Accent palette, kept in sync with `SubjectAccents` in the theme. */
    private val Accents = intArrayOf(
        0xFF9B7BE0.toInt(),
        0xFF6FC7B8.toInt(),
        0xFFE8C36B.toInt(),
        0xFFE59AC4.toInt(),
        0xFFE79A6B.toInt(),
        0xFF7FB3E8.toInt(),
    )

    private class Palette(
        val top: Int,
        val bottom: Int,
        val text: Int,
        val muted: Int,
        val accent: Int,
        val positive: Int,
        val negative: Int,
        val sheenAlpha: Float,
        val hairline: Int,
    )

    private val Light = Palette(
        top = 0xFFFFFFFF.toInt(),
        bottom = 0xFFF1EBFB.toInt(),
        text = 0xFF2A2240.toInt(),
        muted = 0xFF6B5F87.toInt(),
        accent = 0xFF9B7BE0.toInt(),
        positive = 0xFF4E8C84.toInt(),
        negative = 0xFFC9645C.toInt(),
        sheenAlpha = 0.85f,
        hairline = 0x66FFFFFF,
    )

    private val Dark = Palette(
        top = 0xFF2E2842.toInt(),
        bottom = 0xFF191527.toInt(),
        text = 0xFFF0ECF9.toInt(),
        muted = 0xFFB0A6C6.toInt(),
        accent = 0xFFBBA3F0.toInt(),
        positive = 0xFF6FC7B8.toInt(),
        negative = 0xFFE09A94.toInt(),
        sheenAlpha = 0.10f,
        hairline = 0x2EFFFFFF,
    )

    /** Renders one widget card into a bitmap (transparent corners). */
    fun render(
        kind: WidgetKind,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        dark: Boolean,
        snapshot: SchoolSnapshot?,
        average: AverageSummary?,
    ): Bitmap {
        val w = max(widthPx, 48)
        val h = max(heightPx, 48)
        val d = max(density, 1f)
        val palette = if (dark) Dark else Light

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val card = RectF(d, d, w - d, h - d)
        val radius = min(min(w, h) * 0.30f, 26f * d)

        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(card, radius, radius, Path.Direction.CW) })

        // Frosted clay card: gradient, accent bloom, top sheen.
        canvas.drawRoundRect(
            card, radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, card.top, 0f, card.bottom,
                    palette.top, palette.bottom, Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawRect(
            card,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.88f, h * 0.02f, max(w, h) * 0.85f,
                    withAlpha(palette.accent, 0.30f), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawRect(
            RectF(card.left, card.top, card.right, card.top + h * 0.45f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, card.top, 0f, card.top + h * 0.45f,
                    withAlpha(Color.WHITE, palette.sheenAlpha), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            },
        )

        val content = RectF(
            card.left + 14f * d, card.top + 13f * d,
            card.right - 14f * d, card.bottom - 13f * d,
        )
        val values = snapshot?.history.orEmpty().map { it.value.toFloat() }

        when (kind) {
            WidgetKind.AVERAGE -> drawAverage(canvas, content, d, palette, snapshot, average)
            WidgetKind.CURVE -> drawCurve(canvas, content, d, palette, values, "Pas encore d'historique")
            WidgetKind.DUO -> drawDuo(canvas, content, d, palette, snapshot, average, values)
            WidgetKind.GRADE -> drawGrade(canvas, content, d, palette, snapshot)
        }

        // Hairline following the rounded edge — the glass contour.
        canvas.drawRoundRect(
            card.left + 0.5f, card.top + 0.5f, card.right - 0.5f, card.bottom - 0.5f,
            radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1f, d * 0.7f)
                color = palette.hairline
            },
        )
        canvas.restore()
        return bitmap
    }

    // ---- layouts ----------------------------------------------------------

    private fun drawAverage(
        canvas: Canvas, c: RectF, d: Float, p: Palette,
        snapshot: SchoolSnapshot?, average: AverageSummary?,
    ) {
        if (snapshot == null || average == null) {
            drawEmpty(canvas, c, d, p, "Synchronise ton espace élève")
            return
        }
        val eyebrowH = drawEyebrow(canvas, c.left, c.top, "MOYENNE GÉNÉRALE", d, p)
        val gap = 6f * d
        val size = min((c.height() - eyebrowH - gap) / 1.18f, 46f * d).coerceAtLeast(16f * d)
        val numberTop = c.top + eyebrowH + gap
        val numberWidth = drawValue(
            canvas, c.left, numberTop, average.display, "/20", size, d, p, p.text,
        )

        drawDeltaPill(
            canvas, c, numberTop + size * 0.34f, c.left + numberWidth + 8f * d,
            pillText(average), pillColor(average, p), d, p,
        )

        if (c.height() > 120f * d) {
            val sub = bodyPaint(11f * d, p)
            val line = "${snapshot.student.className} • ${snapshot.student.schoolName}"
            drawTextTop(canvas, fit(line, sub, c.width()), c.left, c.bottom - height(sub), sub)
        }
    }

    private fun drawCurve(
        canvas: Canvas, c: RectF, d: Float, p: Palette,
        values: List<Float>, emptyHint: String,
    ) {
        val eyebrowH = drawEyebrow(canvas, c.left, c.top, "ÉVOLUTION", d, p)
        val curve = RectF(c.left, c.top + eyebrowH + 4f * d, c.right, c.bottom)
        if (values.size < 2) {
            val hint = bodyPaint(11f * d, p)
            drawTextTop(canvas, emptyHint, c.left, curve.top + 2f * d, hint)
            drawFlatLine(canvas, curve, d, p)
            return
        }
        drawSparkline(canvas, curve, values, d, p)
    }

    private fun drawDuo(
        canvas: Canvas, c: RectF, d: Float, p: Palette,
        snapshot: SchoolSnapshot?, average: AverageSummary?, values: List<Float>,
    ) {
        if (snapshot == null || average == null) {
            drawCurve(canvas, c, d, p, values, "Synchronise ton espace élève")
            return
        }
        val eyebrowH = drawEyebrow(canvas, c.left, c.top, "MOYENNE GÉNÉRALE", d, p)
        val gap = 5f * d
        val size = min((c.height() * 0.46f - eyebrowH - gap) / 1.18f, 42f * d).coerceAtLeast(14f * d)
        val numberTop = c.top + eyebrowH + gap
        val numberWidth = drawValue(
            canvas, c.left, numberTop, average.display, "/20", size, d, p, p.text,
        )
        drawDeltaPill(
            canvas, c, numberTop + size * 0.34f, c.left + numberWidth + 8f * d,
            pillText(average), pillColor(average, p), d, p,
        )

        val dividerY = numberTop + size * 1.18f + 8f * d
        canvas.drawLine(
            c.left, dividerY, c.right, dividerY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = max(1f, d * 0.7f)
                color = p.hairline
            },
        )
        val curve = RectF(c.left, dividerY + 8f * d, c.right, c.bottom)
        if (values.size >= 2) {
            drawSparkline(canvas, curve, values, d, p)
        } else {
            drawFlatLine(canvas, curve, d, p)
        }
    }

    private fun drawGrade(
        canvas: Canvas, c: RectF, d: Float, p: Palette, snapshot: SchoolSnapshot?,
    ) {
        val latest = snapshot?.grades?.maxByOrNull { it.date }
        if (latest == null) {
            drawEmpty(canvas, c, d, p, "La prochaine note s'affichera ici")
            return
        }
        val subject = snapshot.subjects.find { it.id == latest.subjectId }
        val accent = accentFor(subject?.colorIndex ?: 0)

        val eyebrowH = drawEyebrow(canvas, c.left, c.top, "DERNIÈRE NOTE", d, p)

        // Subject row with its palette dot.
        val name = bodyPaint(12.5f * d, p, bold = true)
        val dotY = c.top + eyebrowH + 6f * d + height(name) * 0.42f
        canvas.drawCircle(
            c.left + 4f * d, dotY, 4f * d,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent },
        )
        drawTextTop(
            canvas, fit(subject?.name ?: "Matière", name, c.width() - 14f * d),
            c.left + 14f * d, c.top + eyebrowH + 6f * d, name,
        )

        val gradeTop = c.top + eyebrowH + 6f * d + height(name) + 6f * d
        val size = min((c.bottom - gradeTop - 4f * d) / 1.18f, 44f * d).coerceAtLeast(14f * d)
        drawValue(
            canvas, c.left, gradeTop,
            AverageCalculator.trim(latest.value), "/" + AverageCalculator.trim(latest.maxValue),
            size, d, p, p.text,
        )

        if (c.height() > 130f * d) {
            val sub = bodyPaint(11f * d, p)
            val line = listOfNotNull(
                formatDate(latest.date),
                latest.title.takeIf { it.isNotBlank() },
            ).joinToString(" • ")
            drawTextTop(
                canvas, fit(line, sub, c.width()), c.left,
                c.bottom - height(sub), sub,
            )
        }
    }

    private fun drawEmpty(
        canvas: Canvas, c: RectF, d: Float, p: Palette, hint: String,
    ) {
        val eyebrowH = drawEyebrow(canvas, c.left, c.top, "ARDOISE", d, p)
        val gap = 8f * d
        val size = min((c.height() - eyebrowH - gap - 18f * d) / 1.18f, 44f * d).coerceAtLeast(14f * d)
        drawTextTop(canvas, "—", c.left, c.top + eyebrowH + gap, numberPaint(size, p.muted, d))
        val hintTop = c.top + eyebrowH + gap + size * 1.18f + 4f * d
        val hintPaint = bodyPaint(11f * d, p)
        if (hintTop + height(hintPaint) <= c.bottom) {
            drawTextTop(canvas, fit(hint, hintPaint, c.width()), c.left, hintTop, hintPaint)
        }
    }

    // ---- primitives -------------------------------------------------------

    private fun drawEyebrow(
        canvas: Canvas, x: Float, top: Float, text: String, d: Float, p: Palette,
    ): Float {
        val paint = textPaint(10f * d, p.muted, family = "sans-serif-medium", spacing = 0.16f)
        drawTextTop(canvas, text, x, top, paint)
        return height(paint)
    }

    /** Draws `value` + small unit; returns the total width drawn. */
    private fun drawValue(
        canvas: Canvas, x: Float, top: Float, value: String, unit: String,
        sizePx: Float, d: Float, p: Palette, color: Int,
    ): Float {
        val paint = numberPaint(sizePx, color, d)
        val baseline = top - paint.ascent()
        canvas.drawText(value, x, baseline, paint)
        val width = paint.measureText(value)

        val unitPaint = textPaint(sizePx * 0.36f, p.muted, family = "sans-serif-medium")
        val unitWidth = unitPaint.measureText(unit)
        canvas.drawText(unit, x + width + 5f * d, baseline - sizePx * 0.10f, unitPaint)
        return width + 5f * d + unitWidth
    }

    private fun drawDeltaPill(
        canvas: Canvas, c: RectF, centerY: Float, minLeft: Float,
        text: String, tone: Int, d: Float, p: Palette,
    ) {
        val paint = textPaint(10.5f * d, tone, family = "sans-serif-medium", spacing = 0.04f)
        val padX = 8f * d
        val padY = 4.5f * d
        val textWidth = paint.measureText(text)
        val pillWidth = textWidth + padX * 2
        val pillHeight = height(paint) + padY * 2
        val right = c.right
        val left = right - pillWidth
        if (left < minLeft) return // not enough room: the number wins

        val rect = RectF(left, centerY - pillHeight / 2f, right, centerY + pillHeight / 2f)
        canvas.drawRoundRect(
            rect, pillHeight / 2f, pillHeight / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(tone, 0.16f) },
        )
        val baseline = rect.top + padY - paint.ascent()
        canvas.drawText(text, rect.left + padX, baseline, paint)
    }

    private fun drawSparkline(
        canvas: Canvas, rect: RectF, values: List<Float>, d: Float, p: Palette,
    ) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val stroke = max(2f, 2.5f * d)
        val padY = stroke * 1.6f
        val top = rect.top + padY
        val bottom = rect.bottom - padY - 2f * d
        if (bottom <= top) return

        // Smooth min/max window (same spirit as the app chart).
        var min = values.min()
        var max = values.max()
        if (max - min < 0.8f) {
            val mid = (max + min) / 2f
            min = mid - 0.4f
            max = mid + 0.4f
        } else {
            min -= 0.3f
            max += 0.3f
        }
        val span = max - min

        val stepX = rect.width() / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Pair(rect.left + i * stepX, bottom - (v - min) / span * (bottom - top))
        }

        val curve = Path().apply {
            moveTo(pts.first().first, pts.first().second)
            for (i in 0 until pts.size - 1) {
                val p0 = pts[maxOf(i - 1, 0)]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts[minOf(i + 2, pts.size - 1)]
                cubicTo(
                    p1.first + (p2.first - p0.first) / 6f,
                    p1.second + (p2.second - p0.second) / 6f,
                    p2.first - (p3.first - p1.first) / 6f,
                    p2.second - (p3.second - p1.second) / 6f,
                    p2.first, p2.second,
                )
            }
        }

        // Area under the curve.
        val area = Path(curve).apply {
            lineTo(pts.last().first, rect.bottom)
            lineTo(pts.first().first, rect.bottom)
            close()
        }
        canvas.drawPath(
            area,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, top, 0f, rect.bottom,
                    withAlpha(p.accent, 0.34f), withAlpha(p.accent, 0f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawPath(
            curve,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = p.accent
            },
        )

        // Glowing endpoint.
        val end = pts.last()
        canvas.drawCircle(
            end.first, end.second, 9f * d,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    end.first, end.second, 9f * d,
                    withAlpha(p.accent, 0.40f), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawCircle(
            end.first, end.second, stroke * 1.5f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent },
        )
    }

    private fun drawFlatLine(canvas: Canvas, rect: RectF, d: Float, p: Palette) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val y = rect.top + rect.height() * 0.70f
        canvas.drawPath(
            Path().apply {
                moveTo(rect.left, y)
                lineTo(rect.right, y)
            },
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(2f, 2f * d)
                strokeCap = Paint.Cap.ROUND
                color = withAlpha(p.muted, 0.45f)
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(9f * d, 9f * d), 0f)
            },
        )
    }

    // ---- text helpers -----------------------------------------------------

    private fun textPaint(
        sizePx: Float, color: Int, family: String = "sans-serif", spacing: Float = 0f,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        this.color = color
        typeface = Typeface.create(family, Typeface.NORMAL)
        letterSpacing = spacing
    }

    private fun numberPaint(sizePx: Float, color: Int, d: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            this.color = color
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            letterSpacing = -0.02f
        }

    private fun bodyPaint(sizePx: Float, p: Palette, bold: Boolean = false): Paint =
        textPaint(
            sizePx, p.muted,
            family = if (bold) "sans-serif-medium" else "sans-serif",
        )

    private fun height(paint: Paint): Float = paint.textSize * 1.24f

    private fun drawTextTop(canvas: Canvas, text: String, x: Float, top: Float, paint: Paint) {
        canvas.drawText(text, x, top - paint.ascent(), paint)
    }

    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var candidate = text
        while (candidate.isNotEmpty() && paint.measureText(candidate + ellipsis) > maxWidth) {
            candidate = candidate.dropLast(1)
        }
        return if (candidate.isEmpty()) ellipsis else candidate + ellipsis
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private fun accentFor(index: Int): Int = Accents[Math.floorMod(index, Accents.size)]

    private fun pillText(average: AverageSummary): String = average.deltaDisplay
        ?: if (average.source == AverageSource.OFFICIAL) "OFFICIELLE" else "CALCULÉE"

    private fun pillColor(average: AverageSummary, p: Palette): Int = when {
        (average.delta ?: 0.0) > 0.0 -> p.positive
        (average.delta ?: 0.0) < 0.0 -> p.negative
        else -> p.accent
    }

    private fun formatDate(iso: String): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)
        if (parsed == null) iso else SimpleDateFormat("d MMM", Locale.FRENCH).format(parsed)
    } catch (_: Exception) {
        iso
    }
}
