package dev.matthewmorris.cuttracker.domain

import dev.matthewmorris.cuttracker.domain.GoalStatus.MET
import dev.matthewmorris.cuttracker.domain.GoalStatus.MISSED
import dev.matthewmorris.cuttracker.domain.GoalStatus.UNMARKED
import org.junit.Assert.assertEquals
import org.junit.Test

class DayColoursTest {
    @Test fun colours() {
        assertEquals(DayColour.GREEN, DayColours.of(listOf(MET, MET)))
        assertEquals(DayColour.AMBER, DayColours.of(listOf(MET, MISSED)))
        assertEquals(DayColour.RED, DayColours.of(listOf(MISSED, MISSED)))
        assertEquals(DayColour.GREY, DayColours.of(listOf(UNMARKED, UNMARKED)))
        assertEquals(DayColour.GREY, DayColours.of(listOf(MET, UNMARKED)))
        assertEquals(DayColour.AMBER, DayColours.of(listOf(MISSED, UNMARKED)))
        assertEquals(DayColour.NONE, DayColours.of(emptyList()))
    }

    @Test fun `a single filtered goal is green or red`() {
        assertEquals(DayColour.GREEN, DayColours.of(listOf(MET)))
        assertEquals(DayColour.RED, DayColours.of(listOf(MISSED)))
    }
}
