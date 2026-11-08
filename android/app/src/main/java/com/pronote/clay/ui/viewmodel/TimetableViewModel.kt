package com.pronote.clay.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pronote.clay.AppContainer
import com.pronote.clay.core.Logger
import com.pronote.clay.core.PronoteResult
import com.pronote.clay.domain.TimetableLesson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Explicit UI state of the timetable screen. */
sealed interface TimetableUiState {
    data object Loading : TimetableUiState

    data class Ready(
        /** Monday of the displayed week. */
        val weekStart: LocalDate,
        val lessons: List<TimetableLesson>,
    ) : TimetableUiState

    data class Error(val message: String) : TimetableUiState
}

/**
 * TimetableViewModel — loads one week of courses per selection.
 *
 * Swiping between days never refetches: the whole week arrives in one
 * repository call and is cached there. Only changing weeks hits the network.
 *
 * The narrow constructor takes the two things it actually needs, so the
 * ViewModel is unit-testable on the JVM without an Android container.
 */
class TimetableViewModel(
    private val loadTimetable: suspend (accountId: String, weekStartIso: String) -> PronoteResult<List<TimetableLesson>>,
    private val activeAccountId: () -> String?,
) : ViewModel() {

    /** Production wiring: same behaviour through the app container. */
    constructor(container: AppContainer) : this(
        loadTimetable = { accountId, weekStartIso ->
            container.getTimetable.execute(accountId, weekStartIso)
        },
        activeAccountId = container::activeAccountId,
    )

    private val _state = MutableStateFlow<TimetableUiState>(TimetableUiState.Loading)
    val state: StateFlow<TimetableUiState> = _state.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(todayIndex())
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    private var weekStart: LocalDate = mondayOf(LocalDate.now())

    init {
        loadWeek(weekStart)
    }

    /** Monday-first week containing today; the view shows 7 day slots. */
    fun weekDays(): List<LocalDate> = (0 until 7).map { weekStart.plusDays(it.toLong()) }

    fun weekLabel(): String {
        // Le bandeau montre les 7 jours (lundi -> dimanche) : le libellé
        // s'arrêtait au samedi, une case avant la fin de la semaine affichée.
        val end = weekStart.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
        return "Semaine du ${weekStart.format(fmt)} au ${end.format(fmt)}"
    }

    fun selectDay(index: Int) {
        _selectedDayIndex.value = index.coerceIn(0, 6)
    }

    fun shiftWeek(delta: Int) {
        val newStart = weekStart.plusWeeks(delta.toLong())
        _selectedDayIndex.value = if (delta > 0) 0 else 6
        loadWeek(newStart)
    }

    /** Retries the week currently on screen, after a failed load. */
    fun reload() {
        loadWeek(weekStart)
    }

    private fun loadWeek(start: LocalDate) {
        weekStart = start
        _state.value = TimetableUiState.Loading
        val accountId = activeAccountId() ?: run {
            _state.value = TimetableUiState.Error("Aucun compte connecté.")
            return
        }

        viewModelScope.launch {
            when (val result = loadTimetable(accountId, start.toString())) {
                is PronoteResult.Success -> {
                    _state.value = TimetableUiState.Ready(
                        weekStart = start,
                        lessons = result.value,
                    )
                }
                is PronoteResult.Failure -> {
                    Logger.warn("Timetable load failed")
                    _state.value = TimetableUiState.Error(
                        result.error.userMessage + " " + (result.error.hint ?: ""),
                    )
                }
            }
        }
    }

    companion object {
        fun mondayOf(date: LocalDate): LocalDate =
            date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** Index of today within its Monday-first week (0..6). */
        fun todayIndex(): Int = LocalDate.now().dayOfWeek.value - 1
    }
}
