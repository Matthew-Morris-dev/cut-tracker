package dev.matthewmorris.cuttracker.domain

enum class DayColour { GREEN, AMBER, RED, GREY, NONE }

object DayColours {
    /**
     * Green = all goals met, red = all missed, amber = some missed, grey = not logged.
     * [statuses] holds one entry per goal that applies to the day; empty means no goal applies.
     */
    fun of(statuses: List<GoalStatus>): DayColour = when {
        statuses.isEmpty() -> DayColour.NONE
        statuses.all { it == GoalStatus.MISSED } -> DayColour.RED
        statuses.any { it == GoalStatus.MISSED } -> DayColour.AMBER
        statuses.all { it == GoalStatus.MET } -> DayColour.GREEN
        else -> DayColour.GREY
    }
}
