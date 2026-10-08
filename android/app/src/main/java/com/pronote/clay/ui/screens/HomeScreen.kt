package com.pronote.clay.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pronote.clay.core.AverageCalculator
import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.Subject
import com.pronote.clay.ui.components.ClayEmptyState
import com.pronote.clay.ui.components.ClayErrorBox
import com.pronote.clay.ui.components.ClayIconButton
import com.pronote.clay.ui.components.ClayPill
import com.pronote.clay.ui.components.ClaySectionTitle
import com.pronote.clay.ui.components.ClaySurface
import com.pronote.clay.ui.components.clay
import com.pronote.clay.ui.components.clayInset
import com.pronote.clay.ui.theme.ClayDimens
import com.pronote.clay.ui.theme.subjectAccent
import com.pronote.clay.ui.viewmodel.HomeUiState
import com.pronote.clay.ui.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HomeScreen — the daily dashboard.
 *
 * Cache-first: the snapshot comes from local storage, so the screen is usable
 * even fully offline. Refreshing, offline and sync-error banners are derived
 * from the explicit [HomeUiState].
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val online by viewModel.online.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    val success = state as? HomeUiState.Success
    val offline = state as? HomeUiState.Offline
    val error = state as? HomeUiState.Error

    val snapshot = success?.snapshot ?: offline?.snapshot ?: error?.snapshot
    val average = success?.average ?: offline?.average
    val subjects = success?.subjects ?: offline?.subjects ?: error?.subjects.orEmpty()
    val refreshing = success?.refreshing ?: false

    if (showSettings) {
        SettingsSheet(viewModel = viewModel, onDismiss = { showSettings = false })
    }

    LazyColumn(
        // The Activity draws edge-to-edge; keep the dashboard inside the
        // status-bar/display-cutout and navigation-bar safe areas.
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(
            start = ClayDimens.ScreenPadding,
            end = ClayDimens.ScreenPadding,
            bottom = 48.dp,
        ),
    ) {
        item { HomeTopBar(snapshot, refreshing, viewModel, onSettings = { showSettings = true }) }
        item { Spacer(Modifier.height(18.dp)) }

        if (!online || offline != null) {
            item { OfflineBanner(snapshot) }
            item { Spacer(Modifier.height(14.dp)) }
        }
        if (error != null) {
            item {
                ClayErrorBox(message = error.message, hint = error.hint)
                Spacer(Modifier.height(14.dp))
            }
        }

        if (average != null) {
            item { AverageCard(average) }
        } else {
            item { ClayEmptyState("Pas encore de moyenne", "Connecte-toi et lance une synchronisation.") }
        }
        item { Spacer(Modifier.height(ClayDimens.SectionGap)) }

    val history = snapshot?.history.orEmpty()
    if (history.size >= 2) {
        item {
            var periodDays by remember { mutableStateOf(com.pronote.clay.data.HistoryPeriods.ALL_DAYS) }

            ClaySectionTitle("Évolution")
            Spacer(Modifier.height(12.dp))
            HistoryPeriodSelector(
                selectedDays = periodDays,
                onSelect = { periodDays = it },
            )
            Spacer(Modifier.height(10.dp))
            HistoryChart(history, periodDays)
        }
        item { Spacer(Modifier.height(ClayDimens.SectionGap)) }
    }

        item {
            ClaySectionTitle("Matières")
            Spacer(Modifier.height(12.dp))
        }
        if (subjects.isEmpty()) {
            item { ClayEmptyState("Aucune matière", "Les matières apparaîtront après une synchronisation.") }
        } else {
            items(subjects, key = { it.subject.id }) { row ->
                SubjectRowCard(row)
                Spacer(Modifier.height(ClayDimens.ItemGap))
            }
        }

        item {
            Spacer(Modifier.height(ClayDimens.SectionGap))
            ClaySectionTitle("Notes récentes")
            Spacer(Modifier.height(12.dp))
        }
        val grades = snapshot?.grades.orEmpty().sortedByDescending { it.date }
        if (grades.isEmpty()) {
            item { ClayEmptyState("Aucune note", "Les nouvelles notes s'afficheront ici.") }
        } else {
            items(grades.take(8), key = { it.id }) { grade ->
                GradeRowCard(grade, snapshot?.subjects)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Name shown after "Bonjour" — a plain function so the priority rule stays
 * testable outside Compose.
 *
 * The name typed in the settings always wins: it is the only one the user can
 * change, while Pronote's payload varies from one school to the next (missing
 * first name, surname only, "NOM PRÉNOM"…).
 */
fun greetingName(
    configured: String?,
    pronoteFirstName: String?,
    pronoteLastName: String?,
): String? {
    configured?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

    return pronoteFirstName?.trim()
        // "Élève" is Mappers' placeholder when Pronote sends no first name.
        ?.takeIf { it.isNotBlank() && it != "Élève" }
        // A "first name" identical to the surname means Pronote never sent one.
        ?.takeUnless { pronoteLastName != null && it.equals(pronoteLastName.trim(), true) }
}

@Composable
private fun HomeTopBar(
    snapshot: com.pronote.clay.domain.SchoolSnapshot?,
    refreshing: Boolean,
    viewModel: HomeViewModel,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val preferences by viewModel.preferences.collectAsState()
        Column(Modifier.weight(1f)) {
            val greeting = greetingName(
                configured = preferences.displayName,
                pronoteFirstName = snapshot?.student?.firstName,
                pronoteLastName = snapshot?.student?.lastName,
            )
            Text(
                if (greeting != null) "Bonjour $greeting" else "Bonjour",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                listOfNotNull(
                    snapshot?.student?.className?.takeIf { it.isNotBlank() },
                    snapshot?.student?.schoolName?.takeIf { it.isNotBlank() },
                ).joinToString(" • ").ifBlank { "Ardoise" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RefreshButton(refreshing) { viewModel.refresh() }
        Spacer(Modifier.width(10.dp))
        ClayIconButton(Icons.Rounded.Settings, "Réglages", onClick = onSettings)
    }
}

@Composable
private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "sync-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "sync-angle",
    )
    androidx.compose.material3.Surface(
        modifier = Modifier
            .size(ClayDimens.MinTouchTarget)
            .clay(depth = 8.dp, shape = RoundedCornerShape(18.dp)),
        color = Color.Transparent,
        onClick = onClick,
    ) {
        Box(Modifier.size(ClayDimens.MinTouchTarget), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "Actualiser",
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (refreshing) angle else 0f),
            )
        }
    }
}

@Composable
private fun OfflineBanner(snapshot: com.pronote.clay.domain.SchoolSnapshot?) {
    ClaySurface(
        modifier = Modifier.fillMaxWidth(),
        depth = 6.dp,
        shape = RoundedCornerShape(20.dp),
        tint = MaterialTheme.colorScheme.secondary,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Hors ligne — données du " + formatTimestamp(snapshot?.fetchedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AverageCard(average: com.pronote.clay.domain.AverageSummary) {
    ClaySurface(modifier = Modifier.fillMaxWidth(), depth = 14.dp) {
        Row(
            Modifier.padding(ClayDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        average.display,
                        fontSize = 52.sp,
                        lineHeight = 54.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp,
                    )
                    Text(
                        " /20",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                ClayPill(
                    text = if (average.source == com.pronote.clay.domain.AverageSource.OFFICIAL) {
                        "Moyenne officielle"
                    } else {
                        "Moyenne calculée"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (average.deltaDisplay != null) {
                val up = (average.delta ?: 0.0) >= 0.0
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        average.deltaDisplay,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (up) Color(0xFF4E8C84) else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "depuis la dernière note",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Period chips for the history chart — 7 jours, 1 mois, 3 mois, Année.
 * Purely visual state: the filtering itself is done in [HistoryChart].
 */
@Composable
private fun HistoryPeriodSelector(
    selectedDays: Long,
    onSelect: (Long) -> Unit,
) {
    val periods = listOf(
        com.pronote.clay.data.HistoryPeriods.WEEK_DAYS to "7 jours",
        com.pronote.clay.data.HistoryPeriods.MONTH_DAYS to "1 mois",
        com.pronote.clay.data.HistoryPeriods.QUARTER_DAYS to "3 mois",
        com.pronote.clay.data.HistoryPeriods.YEAR_DAYS to "Année",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        periods.forEach { (days, label) ->
            val selected = selectedDays == days ||
                (selectedDays == com.pronote.clay.data.HistoryPeriods.ALL_DAYS && days == com.pronote.clay.data.HistoryPeriods.YEAR_DAYS)
            ClayPill(
                text = label,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.pointerInput(days) {
                    // tap to switch period
                    detectTapGestures { onSelect(days) }
                },
            )
        }
    }
}

@Composable
private fun HistoryChart(history: List<com.pronote.clay.domain.HistoryPoint>, periodDays: Long = com.pronote.clay.data.HistoryPeriods.ALL_DAYS) {
    val accent = MaterialTheme.colorScheme.primary
    ClaySurface(modifier = Modifier.fillMaxWidth(), depth = 8.dp) {
        Column(Modifier.padding(18.dp)) {
            val scoped = remember(history, periodDays) {
                com.pronote.clay.data.PronoteRepositoryImpl.filterHistory(history, periodDays)
            }
            val points = if (scoped.size >= 2) scoped else history
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            ) {
                val values = points.map { it.value }
                val min = (values.min() - 0.4).coerceAtLeast(0.0).toFloat()
                val max = (values.max() + 0.4).toFloat()
                val span = (max - min).takeIf { it > 0f } ?: 1f

                fun x(i: Int) = if (points.size == 1) size.width / 2 else i * size.width / (points.size - 1)
                fun y(v: Double) = size.height - ((v.toFloat() - min) / span) * size.height

                val line = Path()
                val fill = Path()
                points.forEachIndexed { i, point ->
                    val px = x(i)
                    val py = y(point.value)
                    if (i == 0) {
                        line.moveTo(px, py)
                        fill.moveTo(px, size.height)
                        fill.lineTo(px, py)
                    } else {
                        line.lineTo(px, py)
                        fill.lineTo(px, py)
                    }
                }
                fill.lineTo(x(points.size - 1), size.height)
                fill.close()

                drawPath(
                    fill,
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    ),
                )
                drawPath(line, color = accent, style = Stroke(width = 7f, cap = StrokeCap.Round))
                drawCircle(accent, radius = 9f, center = androidx.compose.ui.geometry.Offset(x(points.size - 1), y(points.last().value)))
            }
        }
    }
}

@Composable
private fun SubjectRowCard(row: com.pronote.clay.domain.SubjectRow) {
    val accent = subjectAccent(row.subject.colorIndex)
    ClaySurface(
        modifier = Modifier.fillMaxWidth(),
        depth = 6.dp,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clay(depth = 4.dp, shape = androidx.compose.foundation.shape.CircleShape, tint = accent),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.subject.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        row.subject.teacher.takeIf { it.isNotBlank() },
                        "${row.gradeCount} note" + if (row.gradeCount > 1) "s" else "",
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    row.display,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
                if (row.deltaDisplay != null) {
                    val up = (row.delta ?: 0.0) >= 0.0
                    Text(
                        row.deltaDisplay,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (up) Color(0xFF4E8C84) else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun GradeRowCard(grade: Grade, subjects: List<Subject>?) {
    val subject = subjects?.find { it.id == grade.subjectId }
    val accent = subjectAccent(subject?.colorIndex ?: 0)
    ClaySurface(
        modifier = Modifier.fillMaxWidth(),
        depth = 4.dp,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clay(depth = 3.dp, shape = androidx.compose.foundation.shape.CircleShape, tint = accent),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(grade.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        subject?.shortName ?: "—",
                        formatDate(grade.date),
                        "coef. " + AverageCalculator.trim(grade.coefficient),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!grade.countsTowardAverage) {
                ClayPill(
                    text = "non comptée",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                AverageCalculator.formatGrade(grade.value, grade.maxValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private fun formatDate(iso: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)
    if (parsed == null) iso else SimpleDateFormat("d MMM", Locale.FRENCH).format(parsed)
} catch (_: Exception) {
    iso
}

private fun formatTimestamp(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "dernière synchro"
    return SimpleDateFormat("d MMM 'à' HH:mm", Locale.FRENCH).format(Date(epochMs))
}
