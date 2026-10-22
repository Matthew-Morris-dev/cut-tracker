package dev.matthewmorris.cuttracker.domain

/**
 * The day-status rule from the spec, for one goal on one day:
 * 1. Any log that day carries the goal's tag -> missed.
 * 2. Else, the day is over and has at least one log -> met.
 * 3. Else -> unmarked.
 * 4. A manual status, if set, overrides all of the above (see [resolve]).
 */
object DayStatusRule {
    fun autoStatus(avoidTag: String, logTags: List<Set<String>>, dayIsOver: Boolean): GoalStatus = when {
        logTags.any { avoidTag in it } -> GoalStatus.MISSED
        dayIsOver && logTags.isNotEmpty() -> GoalStatus.MET
        else -> GoalStatus.UNMARKED
    }

    fun resolve(auto: GoalStatus, manual: GoalStatus?): GoalStatus = manual ?: auto
}
