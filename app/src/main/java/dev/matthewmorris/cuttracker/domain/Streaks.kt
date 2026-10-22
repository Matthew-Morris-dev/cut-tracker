package dev.matthewmorris.cuttracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** What streaks need to know about a goal. */
data class StreakGoal(val id: Long, val activeFrom: LocalDate, val weeklyAllowance: Int?)

data class StreakResult(val current: Int, val best: Int)

/**
 * Streaks count consecutive days where every goal was met.
 *
 * - A missed day within a goal's weekly allowance (weeks start on Monday) keeps the streak
 *   alive but does not add to it.
 * - A past day that is unmarked breaks the streak: nothing was logged, so nothing was proven.
 *   Today being unmarked is fine, the day isn't over yet.
 */
object Streaks {
    private enum class Outcome { MET, ALLOWED, BROKEN, PENDING, SKIP }

    fun compute(
        goals: List<StreakGoal>,
        status: (date: LocalDate, goalId: Long) -> GoalStatus,
        today: LocalDate,
    ): StreakResult {
        val start = goals.minOfOrNull { it.activeFrom } ?: return StreakResult(0, 0)
        if (start > today) return StreakResult(0, 0)

        val missesThisWeek = mutableMapOf<Pair<Long, LocalDate>, Int>()
        val outcomes = generateSequence(start) { it.plusDays(1) }
            .takeWhile { it <= today }
            .map { date ->
                val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val perGoal = goals.filter { it.activeFrom <= date }.map { goal ->
                    when (status(date, goal.id)) {
                        GoalStatus.MET -> Outcome.MET
                        GoalStatus.MISSED -> {
                            val key = goal.id to weekStart
                            val misses = (missesThisWeek[key] ?: 0) + 1
                            missesThisWeek[key] = misses
                            if (misses <= (goal.weeklyAllowance ?: 0)) Outcome.ALLOWED else Outcome.BROKEN
                        }
                        GoalStatus.UNMARKED -> if (date == today) Outcome.PENDING else Outcome.BROKEN
                    }
                }
                when {
                    perGoal.isEmpty() -> Outcome.SKIP
                    Outcome.BROKEN in perGoal -> Outcome.BROKEN
                    Outcome.PENDING in perGoal -> Outcome.PENDING
                    perGoal.all { it == Outcome.MET } -> Outcome.MET
                    else -> Outcome.ALLOWED
                }
            }
            .toList()

        var best = 0
        var run = 0
        for (outcome in outcomes) {
            when (outcome) {
                Outcome.MET -> run++
                Outcome.BROKEN -> run = 0
                else -> Unit
            }
            best = maxOf(best, run)
        }
        return StreakResult(current = run, best = best)
    }
}
