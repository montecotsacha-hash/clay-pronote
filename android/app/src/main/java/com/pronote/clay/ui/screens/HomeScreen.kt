package com.pronote.clay.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pronote.clay.core.AverageCalculator
import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.Subject
import com.pronote.clay.ui.components.ClayButton
import com.pronote.clay.ui.components.ClayEmptyState
import com.pronote.clay.ui.components.ClayErrorBox
import com.pronote.clay.ui.components.ClayIconButton
import com.pronote.clay.ui.components.ClayPill
import com.pronote.clay.ui.components.ClayReveal
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
 *
 * The screen NEVER draws another screen on top of itself: the timetable and the
 * settings are full sections of their own, reached from the navigation menu.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenMenu: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val online by viewModel.online.collectAsState()

    val success = state as? HomeUiState.Success
    val offline = state as? HomeUiState.Offline
    val issue = state as? HomeUiState.SyncIssue
    val error = state as? HomeUiState.Error

    val snapshot = success?.snapshot ?: offline?.snapshot ?: issue?.snapshot ?: error?.snapshot
    val average = success?.average ?: offline?.average ?: issue?.average
    val subjects = success?.subjects ?: offline?.subjects ?: issue?.subjects ?: error?.subjects.orEmpty()
    val refreshing = success?.refreshing ?: false

    // La note ouverte dans la fiche du bas. La liste ne montre que l'essentiel :
    // le détail vit ici, dans un panneau qui monte du bas.
    var selectedGrade by remember { mutableStateOf<Grade?>(null) }

    Box(Modifier.fillMaxSize()) {
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
        item {
            ClayReveal {
                HomeTopBar(
                    snapshot,
                    refreshing,
                    viewModel,
                    onOpenMenu = onOpenMenu,
                )
            }
        }
        item { Spacer(Modifier.height(18.dp)) }

        homeStatusBanners(
            snapshot = snapshot,
            online = online,
            offline = offline != null,
            issue = issue,
            error = error,
        )

        item {
            ClayReveal(index = 1) {
                if (average != null) {
                    AverageCard(average)
                } else {
                    ClayEmptyState("Pas encore de moyenne", "Connecte-toi et lance une synchronisation.")
                }
            }
        }
        item { Spacer(Modifier.height(ClayDimens.SectionGap)) }

        homeHistorySection(snapshot?.history.orEmpty())
        homeSubjectsSection(subjects)
        homeGradesSection(snapshot) { selectedGrade = it }
    }

        GradeDetailSheet(
            grade = selectedGrade,
            subject = selectedGrade?.let { grade ->
                snapshot?.subjects?.find { it.id == grade.subjectId }
            },
            subjectAverage = selectedGrade?.let { grade ->
                snapshot?.subjectAverages?.find { it.subjectId == grade.subjectId }
            },
            onDismiss = { selectedGrade = null },
        )
    }
}

/** Bandeaux hors ligne, synchro partielle et erreur, au-dessus de la moyenne. */
private fun LazyListScope.homeStatusBanners(
    snapshot: com.pronote.clay.domain.SchoolSnapshot?,
    online: Boolean,
    offline: Boolean,
    issue: HomeUiState.SyncIssue?,
    error: HomeUiState.Error?,
) {
    if (!online || offline) {
        item {
            ClayReveal(index = 1) { OfflineBanner(snapshot) }
            Spacer(Modifier.height(14.dp))
        }
    }
    if (issue != null) {
        item {
            ClayReveal(index = 1) {
                Column {
                    ClayErrorBox(message = issue.message, hint = issue.hint)
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
    if (error != null) {
        item {
            ClayReveal(index = 1) {
                Column {
                    ClayErrorBox(message = error.message, hint = error.hint)
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

/** Courbe d'évolution — masquée tant qu'il n'y a pas deux points à relier. */
private fun LazyListScope.homeHistorySection(history: List<com.pronote.clay.domain.HistoryPoint>) {
    if (history.size < 2) return

    item {
        var periodDays by remember { mutableStateOf(com.pronote.clay.data.HistoryPeriods.ALL_DAYS) }

        ClayReveal(index = 2) {
            Column {
                ClaySectionTitle("Évolution")
                Spacer(Modifier.height(12.dp))
                HistoryPeriodSelector(
                    selectedDays = periodDays,
                    onSelect = { periodDays = it },
                )
                Spacer(Modifier.height(10.dp))
                HistoryChart(history, periodDays)
            }
        }
    }
    item { Spacer(Modifier.height(ClayDimens.SectionGap)) }
}

/** Liste des matières, avec son état vide. */
private fun LazyListScope.homeSubjectsSection(subjects: List<com.pronote.clay.domain.SubjectRow>) {
    item {
        ClayReveal(index = 3) {
            Column {
                ClaySectionTitle("Matières")
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (subjects.isEmpty()) {
        item { ClayEmptyState("Aucune matière", "Les matières apparaîtront après une synchronisation.") }
    } else {
        // `index` feeds the stagger so the subjects cascade down the list
        // instead of all landing on the same frame.
        itemsIndexed(subjects, key = { _, row -> row.subject.id }) { index, row ->
            ClayReveal(index = 3 + index) {
                Column {
                    SubjectRowCard(row)
                    Spacer(Modifier.height(ClayDimens.ItemGap))
                }
            }
        }
    }
}

/** Huit dernières notes, avec leur état vide. */
private fun LazyListScope.homeGradesSection(
    snapshot: com.pronote.clay.domain.SchoolSnapshot?,
    onSelect: (Grade) -> Unit,
) {
    item {
        ClayReveal(index = 5) {
            Column {
                Spacer(Modifier.height(ClayDimens.SectionGap))
                ClaySectionTitle("Notes récentes")
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    val grades = snapshot?.grades.orEmpty().sortedByDescending { it.date }
    if (grades.isEmpty()) {
        item { ClayEmptyState("Aucune note", "Les nouvelles notes s'afficheront ici.") }
    } else {
        itemsIndexed(grades.take(8), key = { _, grade -> grade.id }) { index, grade ->
            ClayReveal(index = 2 + index) {
                Column {
                    GradeRowCard(grade, snapshot?.subjects) { onSelect(grade) }
                    Spacer(Modifier.height(8.dp))
                }
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
    onOpenMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val preferences by viewModel.preferences.collectAsState()
        ClayIconButton(Icons.Rounded.Menu, "Menu", onClick = onOpenMenu)
        Spacer(Modifier.width(14.dp))
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

/**
 * The hero card. The average counts up from zero (or from its previous value
 * after a sync) so the first screen never pops in statically.
 */
@Composable
private fun AverageCard(average: com.pronote.clay.domain.AverageSummary) {
    val animated = remember { Animatable(0f) }

    LaunchedEffect(average.value) {
        animated.animateTo(
            targetValue = average.value.toFloat(),
            animationSpec = tween(durationMillis = 760, easing = FastOutSlowInEasing),
        )
    }

    ClaySurface(modifier = Modifier.fillMaxWidth(), depth = 14.dp) {
        Row(
            Modifier.padding(ClayDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        AverageCalculator.formatAverage(animated.value.toDouble()),
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
            val deltaDisplay = average.deltaDisplay
            if (deltaDisplay != null) {
                // The variation badge slides in *after* the average has counted
                // up, so the eye follows the number before reading the change.
                // A MutableTransitionState is required: `AnimatedVisibility`
                // only animates on a state *change*, not on first composition.
                val deltaVisible = remember { MutableTransitionState(false) }
                deltaVisible.targetState = true
                AnimatedVisibility(
                    visibleState = deltaVisible,
                    enter = fadeIn(tween(360, delayMillis = 260)) +
                        expandHorizontally(tween(360, delayMillis = 260)),
                    exit = fadeOut(tween(120)),
                ) {
                    val up = (average.delta ?: 0.0) >= 0.0
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            deltaDisplay,
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
}

/**
 * Period chips for the history chart — 7 jours, 1 mois, 3 mois, Année.
 *
 * The selected chip scales up while the others settle back down, and the whole
 * row animates instead of swapping colour in a single frame.
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
            val chipScale by animateFloatAsState(
                targetValue = if (selected) 1.06f else 0.96f,
                animationSpec = tween(220),
                label = "period-chip",
            )
            ClayPill(
                text = label,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.scale(chipScale),
                onClick = { onSelect(days) },
            )
        }
    }
}

/**
 * The evolution curve. The line draws itself left to right and the area under
 * it fades in with the same progress, then the endpoint dot lands — so a period
 * change reads as a movement instead of a jump cut.
 */
@Composable
private fun HistoryChart(history: List<com.pronote.clay.domain.HistoryPoint>, periodDays: Long = com.pronote.clay.data.HistoryPeriods.ALL_DAYS) {
    val accent = MaterialTheme.colorScheme.primary
    ClaySurface(modifier = Modifier.fillMaxWidth(), depth = 8.dp) {
        Column(Modifier.padding(18.dp)) {
            val scoped = remember(history, periodDays) {
                com.pronote.clay.data.PronoteRepositoryImpl.filterHistory(history, periodDays)
            }
            val points = if (scoped.size >= 2) scoped else history

            val draw = remember { Animatable(0f) }
            LaunchedEffect(points) {
                draw.snapTo(0f)
                draw.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing),
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            ) {
                val values = points.map { it.value }
                val min = (values.min() - 0.4).coerceAtLeast(0.0).toFloat()
                val max = (values.max() + 0.4).toFloat()
                val span = (max - min).takeIf { it > 0f } ?: 1f
                val progress = draw.value

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
                    alpha = progress,
                )

                val measure = PathMeasure().apply { setPath(line, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * progress, drawn, true)
                drawPath(drawn, color = accent, style = Stroke(width = 7f, cap = StrokeCap.Round))

                if (progress > 0.98f) {
                    drawCircle(
                        accent,
                        radius = 9f,
                        center = androidx.compose.ui.geometry.Offset(x(points.size - 1), y(points.last().value)),
                    )
                }
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

/**
 * Une ligne de note, volontairement dépouillée : l'intitulé et la note.
 *
 * Matière, date et coefficient remplissaient la liste sans aider à lire les
 * notes ; ils vivent désormais dans la fiche qui s'ouvre au toucher.
 */
@Composable
private fun GradeRowCard(grade: Grade, subjects: List<Subject>?, onClick: () -> Unit) {
    val subject = subjects?.find { it.id == grade.subjectId }
    val accent = subjectAccent(subject?.colorIndex ?: 0)
    ClaySurface(
        modifier = Modifier.fillMaxWidth(),
        depth = 4.dp,
        shape = RoundedCornerShape(18.dp),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clay(depth = 3.dp, shape = androidx.compose.foundation.shape.CircleShape, tint = accent),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                grade.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                AverageCalculator.formatGrade(grade.value, grade.maxValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/**
 * Fiche d'une note — le pendant du geste « tap » sur la liste.
 *
 * Elle monte du bas avec un ressort légèrement rebondissant, façon feuille
 * iOS, et montre tout ce que la ligne a retiré : matière, professeur, barème,
 * coefficient, date longue, moyenne de la matière et commentaire.
 * Fermeture au toucher du fond, au bouton retour ou au bouton « Fermer ».
 */
@Composable
private fun GradeDetailSheet(
    grade: Grade?,
    subject: Subject?,
    subjectAverage: com.pronote.clay.domain.SubjectAverage?,
    onDismiss: () -> Unit,
) {
    // La note reste affichée le temps de l'animation de sortie.
    var shown by remember { mutableStateOf<Pair<Grade, Subject?>?>(null) }
    LaunchedEffect(grade) { if (grade != null) shown = grade to subject }

    AnimatedVisibility(
        visible = grade != null,
        enter = slideInVertically(
            initialOffsetY = { height -> height },
            // Rebond volontairement marqué : le panneau dépasse sa position
            // puis se stabilise, au lieu de s'arrêter net.
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        ) + fadeIn(animationSpec = tween(140)),
        exit = slideOutVertically(
            targetOffsetY = { height -> height },
            animationSpec = tween(180),
        ) + fadeOut(animationSpec = tween(160)),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            shown?.let { (current, currentSubject) ->
                GradeDetailCard(
                    grade = current,
                    subject = currentSubject,
                    subjectAverage = subjectAverage,
                    onDismiss = onDismiss,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun GradeDetailCard(
    grade: Grade,
    subject: Subject?,
    subjectAverage: com.pronote.clay.domain.SubjectAverage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val accent = subjectAccent(subject?.colorIndex ?: 0)

    ClaySurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        depth = 16.dp,
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 44.dp, height = 5.dp)
                    .clay(
                        depth = 2.dp,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    ),
            )
            Spacer(Modifier.height(16.dp))
            Text(grade.title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clay(depth = 3.dp, shape = androidx.compose.foundation.shape.CircleShape, tint = accent),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(
                        subject?.name ?: "Matière inconnue",
                        subject?.teacher?.takeIf { it.isNotBlank() },
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    AverageCalculator.formatGrade(grade.value, grade.maxValue),
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Black,
                )
                if (!grade.countsTowardAverage) {
                    Spacer(Modifier.width(10.dp))
                    ClayPill(
                        text = "non comptée",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            GradeDetailRow("Date", longDate(grade.date))
            GradeDetailRow("Coefficient", AverageCalculator.trim(grade.coefficient))
            GradeDetailRow("Barème", "sur " + AverageCalculator.trim(grade.maxValue))
            GradeDetailRow(
                "Comptée dans la moyenne",
                if (grade.countsTowardAverage) "oui" else "non",
            )
            if (subjectAverage != null) {
                GradeDetailRow(
                    "Moyenne de la matière",
                    AverageCalculator.formatAverage(subjectAverage.value) + "/20" +
                        if (subjectAverage.source == com.pronote.clay.domain.AverageSource.CALCULATED) {
                            " (calculée)"
                        } else {
                            ""
                        },
                )
            }
            grade.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                Spacer(Modifier.height(14.dp))
                ClaySurface(
                    modifier = Modifier.fillMaxWidth(),
                    depth = 3.dp,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        comment,
                        Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            ClayButton(text = "Fermer", onClick = onDismiss)
        }
    }
}

@Composable
private fun GradeDetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun longDate(iso: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)
    if (parsed == null) {
        iso
    } else {
        SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(parsed)
    }
} catch (_: Exception) {
    iso
}

private fun formatTimestamp(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "dernière synchro"
    return SimpleDateFormat("d MMM 'à' HH:mm", Locale.FRENCH).format(Date(epochMs))
}
