package dev.matthewmorris.cuttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TrendsTest {
    private val end = LocalDate.of(2026, 10, 22)

    @Test fun `averages the last 7 days only`() {
        val readings = listOf(
            DailyReading(end.minusDays(7), 100.0),
            DailyReading(end.minusDays(6), 84.0),
            DailyReading(end, 83.0),
        )
        assertEquals(83.5, Trends.sevenDayAverage(readings, end)!!, 1e-9)
    }

    @Test fun `several readings on one day count once`() {
        val readings = listOf(
            DailyReading(end, 83.0),
            DailyReading(end, 85.0),
            DailyReading(end.minusDays(1), 86.0),
        )
        assertEquals(85.0, Trends.sevenDayAverage(readings, end)!!, 1e-9)
    }

    @Test fun `no readings gives no average`() {
        assertNull(Trends.sevenDayAverage(emptyList(), end))
    }

    @Test fun `weekly change compares this week's average with last week's`() {
        val readings = listOf(DailyReading(end.minusDays(7), 84.0), DailyReading(end, 83.4))
        assertEquals(-0.6, Trends.weeklyChange(readings, end)!!, 1e-9)
    }

    @Test fun `daily means are one per day, oldest first`() {
        val readings = listOf(
            DailyReading(end, 83.0),
            DailyReading(end.minusDays(1), 84.0),
            DailyReading(end, 84.0),
        )
        assertEquals(
            listOf(DailyReading(end.minusDays(1), 84.0), DailyReading(end, 83.5)),
            Trends.dailyMeans(readings),
        )
    }

    @Test fun `moving average skips days with nothing in their window`() {
        val readings = listOf(DailyReading(end.minusDays(9), 85.0), DailyReading(end, 83.0))
        val series = Trends.movingAverage(readings, end.minusDays(10), end)
        // Day -9 covers days -9 to -3; days -2 to -1 have no reading in their window; day 0 sees only itself.
        assertEquals((9 downTo 3).map { end.minusDays(it.toLong()) } + end, series.map { it.date })
        assertEquals(83.0, series.last().value, 1e-9)
    }
}

