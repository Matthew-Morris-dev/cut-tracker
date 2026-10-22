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
}
