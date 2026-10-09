package com.pronote.clay.ui.viewmodel

import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import com.pronote.clay.domain.TimetableLesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * JVM unit tests of the timetable ViewModel: week maths, day selection and
 * the three UI states (Ready / Error / no account), with a fake loader.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun lesson(name: String, dayOffset: Int, hour: Int) = TimetableLesson(
        subjectId = name.lowercase(),
        subjectName = name,
        teacher = null,
        room = null,
        group = null,
        startIso = "2026-10-%02dT%02d:00:00".format(5 + dayOffset, hour),
        endIso = "2026-10-%02dT%02d:00:00".format(5 + dayOffset, hour + 1),
        cancelled = false,
        memo = null,
    )

    @Test
    fun `starts on today's day index and loads the current week`() = runTest(dispatcher) {
        val requested = mutableListOf<Pair<String, String>>()
        val vm = TimetableViewModel(
            loadTimetable = { accountId, weekStartIso ->
                requested += accountId to weekStartIso
                PronoteResult.Success(emptyList())
            },
            activeAccountId = { "acct" },
        )

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LocalDate.now().dayOfWeek.value - 1, vm.selectedDayIndex.value)
        assertEquals(1, requested.size)
        assertEquals(TimetableViewModel.mondayOf(LocalDate.now()).toString(), requested[0].second)
        assertTrue(vm.state.value is TimetableUiState.Ready)
    }

    @Test
    fun `weekDays are Monday-first and cover seven days`() {
        val vm = TimetableViewModel(
            loadTimetable = { _, _ -> PronoteResult.Success(emptyList()) },
            activeAccountId = { "acct" },
        )
        dispatcher.scheduler.advanceUntilIdle()

        val days = vm.weekDays()
        assertEquals(7, days.size)
        assertEquals(java.time.DayOfWeek.MONDAY, days[0].dayOfWeek)
        assertEquals(java.time.DayOfWeek.SUNDAY, days[6].dayOfWeek)
    }

    @Test
    fun `selectDay is clamped into the week`() {
        val vm = TimetableViewModel(
            loadTimetable = { _, _ -> PronoteResult.Success(emptyList()) },
            activeAccountId = { "acct" },
        )
        vm.selectDay(-3)
        assertEquals(0, vm.selectedDayIndex.value)
        vm.selectDay(99)
        assertEquals(6, vm.selectedDayIndex.value)
    }

    @Test
    fun `shifting weeks requests the new Monday and lands on Monday or Sunday`() = runTest(dispatcher) {
        val requested = mutableListOf<String>()
        val vm = TimetableViewModel(
            loadTimetable = { _, weekStartIso ->
                requested += weekStartIso
                PronoteResult.Success(emptyList())
            },
            activeAccountId = { "acct" },
        )
        dispatcher.scheduler.advanceUntilIdle()

        requested.clear()
        vm.shiftWeek(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, requested.size)
        assertEquals(
            TimetableViewModel.mondayOf(LocalDate.now()).plusWeeks(1).toString(),
            requested[0],
        )
        assertEquals(0, vm.selectedDayIndex.value)
    }

    @Test
    fun `a failed load surfaces the human message and hint`() = runTest(dispatcher) {
        val vm = TimetableViewModel(
            loadTimetable = { _, _ -> PronoteResult.Failure(PronoteError.SessionExpired) },
            activeAccountId = { "acct" },
        )
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is TimetableUiState.Error)
        assertTrue((state as TimetableUiState.Error).message.contains("expiré", ignoreCase = true))
        assertTrue(state.message.contains("Reconnecte-toi"))
    }

    @Test
    fun `without a connected account the error is immediate and no load happens`() = runTest(dispatcher) {
        var loadCalls = 0
        val vm = TimetableViewModel(
            loadTimetable = { _, _ ->
                loadCalls++
                PronoteResult.Success(emptyList())
            },
            activeAccountId = { null },
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, loadCalls)
        assertEquals("Aucun compte connecté.", (vm.state.value as TimetableUiState.Error).message)
    }
}
