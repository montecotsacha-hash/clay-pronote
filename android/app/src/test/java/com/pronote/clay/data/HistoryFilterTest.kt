package com.pronote.clay.data

import com.pronote.clay.domain.HistoryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * The period filter behind the history chart: 7 days / 1 month / 3 months /
 * year. Pure function, no Pronote and no storage involved.
 */
class HistoryFilterTest {

    /** Fixed "now" — 2026-10-08 12:00 UTC — so tests never depend on the clock. */
    private val todayMs: Long = utc(2026, Calendar.OCTOBER, 8)

    private fun utc(year: Int, month: Int, day: Int): Long =
        GregorianCalendar(year, month, day, 12, 0, 0).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.timeInMillis

    private fun iso(daysAgo: Long): String {
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = todayMs - daysAgo * HistoryPeriods.DAY_MS
        }
        val y = cal.get(Calendar.YEAR)
        val m = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val d = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        return "$y-$m-$d"
    }

    /** One point per week over ~14 months, plus "today". */
    private val richHistory: List<HistoryPoint> = buildList {
        (56L downTo 1).forEach { weeksAgo ->
            add(HistoryPoint(iso(weeksAgo * 7), 10.0 + weeksAgo % 5))
        }
        add(HistoryPoint(iso(0), 12.6)) // today, always kept
    }

    @Test
    fun `une semaine ne garde que les 7 derniers jours plus aujourd'hui`() {
        val filtered = PronoteRepositoryImpl.filterHistory(richHistory, HistoryPeriods.WEEK_DAYS, todayMs)
        assertTrue(filtered.size <= 3)
        // The "today" point always survives so the chart ends on the current average.
        assertEquals(12.6, filtered.last().value, 1e-9)
    }

    @Test
    fun `un mois garde uniquement les 30 derniers jours`() {
        val filtered = PronoteRepositoryImpl.filterHistory(richHistory, HistoryPeriods.MONTH_DAYS, todayMs)
        assertTrue(filtered.size <= 6)
        assertTrue(filtered.all { it == richHistory.last() || iso(0) >= it.date })
    }

    @Test
    fun `trois mois garde plus de points qu'un mois`() {
        val month = PronoteRepositoryImpl.filterHistory(richHistory, HistoryPeriods.MONTH_DAYS, todayMs)
        val quarter = PronoteRepositoryImpl.filterHistory(richHistory, HistoryPeriods.QUARTER_DAYS, todayMs)
        assertTrue(quarter.size > month.size)
    }

    @Test
    fun `l'année ne filtre rien`() {
        assertEquals(richHistory, PronoteRepositoryImpl.filterHistory(richHistory, HistoryPeriods.YEAR_DAYS, todayMs))
        assertEquals(richHistory, PronoteRepositoryImpl.filterHistory(richHistory, HistoryPeriods.ALL_DAYS, todayMs))
    }

    @Test
    fun `une fenetre trop pauvre retombe sur l'historique complet`() {
        val tiny = listOf(
            HistoryPoint(iso(40), 11.0),
            HistoryPoint(iso(0), 12.6),
        )
        assertEquals(tiny, PronoteRepositoryImpl.filterHistory(tiny, HistoryPeriods.WEEK_DAYS, todayMs))
    }

    @Test
    fun `l'historique vide reste vide`() {
        assertEquals(emptyList<HistoryPoint>(), PronoteRepositoryImpl.filterHistory(emptyList(), HistoryPeriods.WEEK_DAYS, todayMs))
    }

    @Test
    fun `une date imparsable est conservée plutôt que perdue`() {
        val weird = listOf(
            HistoryPoint("pas-une-date", 11.0),
            HistoryPoint(iso(0), 12.6),
        )
        // 2 points already at the minimum, so they all stay.
        assertEquals(2, PronoteRepositoryImpl.filterHistory(weird, HistoryPeriods.WEEK_DAYS, todayMs).size)
    }
}
