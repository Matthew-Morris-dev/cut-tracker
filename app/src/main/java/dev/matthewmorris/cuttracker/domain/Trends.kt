package dev.matthewmorris.cuttracker.domain

import java.time.LocalDate

data class DailyReading(val date: LocalDate, val value: Double)

object Trends {
    /**
     * Mean of the daily means over the 7 days ending on [end]. Several readings on one day count
     * once, so weighing twice doesn't skew the week. Null when there is no reading in the window.
     */
    fun sevenDayAverage(readings: List<DailyReading>, end: LocalDate): Double? {
        val from = end.minusDays(6)
        val dailyMeans = readings
            .filter { it.date in from..end }
            .groupBy { it.date }
            .map { (_, day) -> day.map { it.value }.average() }
        return if (dailyMeans.isEmpty()) null else dailyMeans.average()
    }

    /** One mean per day, oldest first: the dots on the chart. */
    fun dailyMeans(readings: List<DailyReading>): List<DailyReading> = readings
        .groupBy { it.date }
        .map { (date, day) -> DailyReading(date, day.map { it.value }.average()) }
        .sortedBy { it.date }

    /** The 7-day average for each day from [from] to [to] that has a reading in its window: the line. */
    fun movingAverage(readings: List<DailyReading>, from: LocalDate, to: LocalDate): List<DailyReading> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { it <= to }
            .mapNotNull { day -> sevenDayAverage(readings, day)?.let { DailyReading(day, it) } }
            .toList()

    /** Change in the 7-day average over the last week, in units per week. Negative is a loss. */
    fun weeklyChange(readings: List<DailyReading>, end: LocalDate): Double? {
        val now = sevenDayAverage(readings, end) ?: return null
        val weekAgo = sevenDayAverage(readings, end.minusDays(7)) ?: return null
        return now - weekAgo
    }
}
