package dev.matthewmorris.cuttracker.domain

import dev.matthewmorris.cuttracker.domain.GoalStatus.MET
import dev.matthewmorris.cuttracker.domain.GoalStatus.MISSED
import dev.matthewmorris.cuttracker.domain.GoalStatus.UNMARKED
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    // 2026-10-05 is a Monday.
    private val monday = LocalDate.of(2026, 10, 5)

    private fun streak(days: List<GoalStatus>, allowance: Int? = null, today: LocalDate = monday.plusDays(days.size - 1L)): StreakResult {
        val goal = StreakGoal(1, monday, allowance)
        return Streaks.compute(listOf(goal), { date, _ -> days.getOrElse((date.toEpochDay() - monday.toEpochDay()).toInt()) { UNMARKED } }, today)
    }

    @Test fun `counts consecutive met days`() {
        assertEquals(StreakResult(current = 3, best = 3), streak(listOf(MET, MET, MET)))
    }

    @Test fun `a miss resets the current streak but keeps the best`() {
        assertEquals(StreakResult(current = 1, best = 2), streak(listOf(MET, MET, MISSED, MET)))
    }

    @Test fun `today unmarked does not break the streak`() {
        assertEquals(StreakResult(current = 2, best = 2), streak(listOf(MET, MET, UNMARKED)))
    }

    @Test fun `a past unmarked day breaks the streak`() {
        assertEquals(StreakResult(current = 1, best = 1), streak(listOf(MET, UNMARKED, MET)))
    }

    @Test fun `a miss within the weekly allowance keeps the streak without adding to it`() {
        assertEquals(StreakResult(current = 3, best = 3), streak(listOf(MET, MISSED, MET, MET), allowance = 1))
    }

    @Test fun `a second miss in the same week breaks the streak`() {
        assertEquals(StreakResult(current = 1, best = 2), streak(listOf(MET, MISSED, MET, MISSED, MET), allowance = 1))
    }

    @Test fun `the allowance resets on Monday`() {
        // Missed on Sunday (day 6) and again on the next Monday (day 7): one miss in each week.
        val days = listOf(MET, MET, MET, MET, MET, MET, MISSED, MISSED, MET)
        assertEquals(StreakResult(current = 7, best = 7), streak(days, allowance = 1))
    }

    @Test fun `all goals must be met for the day to count`() {
        val goals = listOf(StreakGoal(1, monday, null), StreakGoal(2, monday, null))
        val status = { date: LocalDate, id: Long -> if (id == 2L && date == monday.plusDays(1)) MISSED else MET }
        assertEquals(StreakResult(current = 1, best = 1), Streaks.compute(goals, status, monday.plusDays(2)))
    }

    @Test fun `no goals means no streak`() {
        assertEquals(StreakResult(0, 0), Streaks.compute(emptyList(), { _, _ -> MET }, monday))
    }
}
