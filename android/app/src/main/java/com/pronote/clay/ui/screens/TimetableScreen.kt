package com.pronote.clay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pronote.clay.domain.TimetableLesson
import com.pronote.clay.ui.components.ClayEmptyState
import com.pronote.clay.ui.components.ClayPill
import com.pronote.clay.ui.components.ClaySurface
import com.pronote.clay.ui.components.clay
import com.pronote.clay.ui.theme.ClayDimens
import com.pronote.clay.ui.theme.subjectAccent
import com.pronote.clay.ui.viewmodel.TimetableUiState
import com.pronote.clay.ui.viewmodel.TimetableViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DAY_NAMES = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/**
 * TimetableScreen — the week schedule.
 *
 * One day at a time, swiped horizontally; a week strip on top lets the user
 * jump straight to a day. The whole state machine lives in the ViewModel.
 */
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val selectedDay by viewModel.selectedDayIndex.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TimetableTopBar(onBack = onBack)
        WeekStrip(
            weekDays = viewModel.weekDays(),
            selectedIndex = selectedDay,
            onSelect = viewModel::selectDay,
            onPrevWeek = { viewModel.shiftWeek(-1) },
            onNextWeek = { viewModel.shiftWeek(1) },
            weekLabel = viewModel.weekLabel(),
        )

        Box(Modifier.fillMaxSize()) {
            when (val current = state) {
                is TimetableUiState.Loading -> LoadingColumn()
                is TimetableUiState.Error -> ErrorColumn(current.message)
                is TimetableUiState.Ready -> DayContent(
                    state = current,
                    dayIndex = selectedDay,
                    onSwipe = viewModel::selectDay,
                )
            }
        }
    }
}

@Composable
private fun TimetableTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
        }
        Text(
            "Emploi du temps",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
    }
}

/** Week strip: 7 day dots + the week label and week-switch arrows. */
@Composable
private fun WeekStrip(
    weekDays: List<java.time.LocalDate>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    weekLabel: String,
) {
    ClaySurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClayDimens.ScreenPadding, vertical = 4.dp),
        depth = 6.dp,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevWeek, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Semaine précédente", modifier = Modifier.size(20.dp))
                }
                Text(
                    weekLabel,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNextWeek, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Semaine suivante", modifier = Modifier.size(20.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weekDays.forEachIndexed { index, date ->
                    val selected = index == selectedIndex
                    val isToday = date == java.time.LocalDate.now()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clay(
                                    depth = if (selected) 6.dp else 2.dp,
                                    shape = CircleShape,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected || isToday) FontWeight.Black else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            DAY_NAMES[valueDayIndex(date)],
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

private fun valueDayIndex(date: java.time.LocalDate): Int =
    date.dayOfWeek.value - 1 // java: MONDAY=1..SUNDAY=7 → 0..6

@Composable
private fun LoadingColumn() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clay(depth = 8.dp, shape = CircleShape, tint = MaterialTheme.colorScheme.primary),
        )
        Text(
            "Chargement de la semaine…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun ErrorColumn(message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(ClayDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ClayEmptyState("Emploi du temps indisponible", message)
    }
}

/** One day: animated swipe left/right between days. */
@Composable
private fun DayContent(
    state: TimetableUiState.Ready,
    dayIndex: Int,
    onSwipe: (Int) -> Unit,
) {
    val total = 7
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(total) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount < -40 && dayIndex < total - 1) onSwipe(dayIndex + 1)
                    if (dragAmount > 40 && dayIndex > 0) onSwipe(dayIndex - 1)
                }
            },
    ) {
        AnimatedContent(
            targetState = dayIndex,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(260)) { if (forward) it else -it } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(260)) { if (forward) -it else it } + fadeOut(tween(180)))
            },
            label = "day-swipe",
        ) { index ->
            val date = state.weekStart.plusDays(index.toLong())
            val dayLessons = state.lessons.filter { it.day == date.toString() }
            LessonList(
                date = date,
                lessons = dayLessons,
                isToday = date == java.time.LocalDate.now(),
            )
        }
    }
}

/** Vertical list of the day's courses, newest at top by start time. */
@Composable
private fun LessonList(
    date: java.time.LocalDate,
    lessons: List<TimetableLesson>,
    isToday: Boolean,
) {
    if (lessons.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ClayEmptyState(
                "Aucun cours",
                if (isToday) "Profite de ta journée !" else "Pas de cours ce jour-là.",
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = ClayDimens.ScreenPadding,
            end = ClayDimens.ScreenPadding,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "${DAY_NAMES[valueDayIndex(date)]} ${date.dayOfMonth} " +
                    date.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.FRENCH)
                    .replaceFirstChar { it.uppercase() } +
                    if (isToday) "  •  aujourd'hui" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(lessons, key = { it.startIso + it.subjectName }) { lesson ->
            LessonCard(lesson)
        }
    }
}

@Composable
private fun LessonCard(lesson: TimetableLesson) {
    val accent = if (lesson.cancelled) Color(0xFFB0757B) else MaterialTheme.colorScheme.primary
    ClaySurface(
        modifier = Modifier.fillMaxWidth(),
        depth = 6.dp,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Time column
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    lesson.startTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    lesson.endTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier
                    .size(12.dp)
                    .clay(depth = 3.dp, shape = CircleShape, tint = accent),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    lesson.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    // Cancelled courses read through a soft strike.
                    textDecoration = if (lesson.cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                listOfNotNull(
                    lesson.teacher,
                    lesson.room?.let { "Salle $it" },
                    lesson.group,
                ).joinToString(" • ").takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                lesson.memo?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (lesson.cancelled) {
                ClayPill(text = "Annulé", tint = Color(0xFFB0757B), icon = Icons.Rounded.EventBusy)
            }
        }
    }
}
