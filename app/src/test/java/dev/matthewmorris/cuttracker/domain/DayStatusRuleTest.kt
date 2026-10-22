package dev.matthewmorris.cuttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DayStatusRuleTest {
    private val beer = setOf(Tags.ALCOHOL)
    private val chicken = emptySet<String>()

    @Test fun `a tagged log misses the goal even before the day is over`() {
        assertEquals(GoalStatus.MISSED, DayStatusRule.autoStatus(Tags.ALCOHOL, listOf(chicken, beer), dayIsOver = false))
    }

    @Test fun `a finished day with untagged logs is met`() {
        assertEquals(GoalStatus.MET, DayStatusRule.autoStatus(Tags.ALCOHOL, listOf(chicken), dayIsOver = true))
    }

    @Test fun `today with untagged logs stays unmarked until the day is over`() {
        assertEquals(GoalStatus.UNMARKED, DayStatusRule.autoStatus(Tags.ALCOHOL, listOf(chicken), dayIsOver = false))
    }

    @Test fun `a finished day with nothing logged stays unmarked`() {
        assertEquals(GoalStatus.UNMARKED, DayStatusRule.autoStatus(Tags.ALCOHOL, emptyList(), dayIsOver = true))
    }

    @Test fun `another goal's tag does not miss this goal`() {
        assertEquals(GoalStatus.MET, DayStatusRule.autoStatus(Tags.TREAT, listOf(beer), dayIsOver = true))
    }

    @Test fun `manual status beats auto`() {
        assertEquals(GoalStatus.MISSED, DayStatusRule.resolve(GoalStatus.MET, GoalStatus.MISSED))
        assertEquals(GoalStatus.MET, DayStatusRule.resolve(GoalStatus.MISSED, GoalStatus.MET))
        assertEquals(GoalStatus.MISSED, DayStatusRule.resolve(GoalStatus.MISSED, null))
    }
}
