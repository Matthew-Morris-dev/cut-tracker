package dev.matthewmorris.cuttracker.data

import androidx.room.withTransaction
import dev.matthewmorris.cuttracker.data.db.AppDatabase
import dev.matthewmorris.cuttracker.data.db.DayGoalStatus
import dev.matthewmorris.cuttracker.domain.DayStatusRule
import dev.matthewmorris.cuttracker.domain.GoalStatus
import dev.matthewmorris.cuttracker.domain.StatusSource
import java.time.LocalDate

/**
 * Keeps the stored auto statuses in line with the day-status rule. Run when a log changes and
 * whenever the app comes to the foreground (which is what turns yesterday's statuses into met).
 * Manual statuses are never touched here.
 */
class GoalStatusResolver(private val db: AppDatabase, private val today: () -> LocalDate) {
    private val goalDao = db.goalDao()
    private val foodDao = db.foodDao()

    suspend fun resolve(from: LocalDate, to: LocalDate) = db.withTransaction {
        val now = today()
        val goals = goalDao.getActiveGoals()
        val logTags = foodDao.logTagsBetween(from, to).groupBy({ it.date }, { it.tags })
        val existing = goalDao.getStatusesBetween(from, to).associateBy { it.date to it.goalId }

        var date = from
        while (date <= to) {
            for (goal in goals) {
                if (date < goal.activeFrom) continue
                val current = existing[date to goal.id]
                if (current?.source == StatusSource.MANUAL) continue
                val auto = DayStatusRule.autoStatus(goal.avoidTag, logTags[date].orEmpty(), dayIsOver = date < now)
                when {
                    auto == GoalStatus.UNMARKED -> if (current != null) goalDao.deleteStatus(date, goal.id)
                    current?.status != auto -> goalDao.upsertStatus(DayGoalStatus(date, goal.id, auto, StatusSource.AUTO))
                }
            }
            date = date.plusDays(1)
        }
    }

    suspend fun resolveAll() {
        val from = listOfNotNull(foodDao.earliestLogDate(), goalDao.earliestActiveFrom()).minOrNull() ?: return
        resolve(from, maxOf(from, today()))
    }

    /** Sets a manual override, or clears it (back to the auto rule) when [status] is null. */
    suspend fun setManual(date: LocalDate, goalId: Long, status: GoalStatus?) {
        if (status == null) {
            goalDao.deleteStatus(date, goalId)
            resolve(date, date)
        } else {
            goalDao.upsertStatus(DayGoalStatus(date, goalId, status, StatusSource.MANUAL))
        }
    }
}
