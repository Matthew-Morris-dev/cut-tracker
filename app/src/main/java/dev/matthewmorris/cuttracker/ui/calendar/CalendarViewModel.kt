package dev.matthewmorris.cuttracker.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matthewmorris.cuttracker.AppContainer
import dev.matthewmorris.cuttracker.data.db.DayGoalStatus
import dev.matthewmorris.cuttracker.data.db.FoodLog
import dev.matthewmorris.cuttracker.data.db.Goal
import dev.matthewmorris.cuttracker.domain.DayColour
import dev.matthewmorris.cuttracker.domain.DayColours
import dev.matthewmorris.cuttracker.domain.GoalStatus
import dev.matthewmorris.cuttracker.domain.StatusSource
import dev.matthewmorris.cuttracker.domain.StreakGoal
import dev.matthewmorris.cuttracker.domain.StreakResult
import dev.matthewmorris.cuttracker.domain.Streaks
import dev.matthewmorris.cuttracker.domain.Tags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarDay(val date: LocalDate, val colour: DayColour, val isToday: Boolean)

data class CalendarState(
    val month: YearMonth,
    val days: List<CalendarDay> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val filterGoalId: Long? = null,
    val streak: StreakResult = StreakResult(0, 0),
    /** Days this month the filtered goal was missed, or with no filter, days an alcohol goal was missed. */
    val missedThisMonth: Int? = null,
)

data class DayGoal(val goal: Goal, val status: GoalStatus, val manual: Boolean)

data class DayDetail(val date: LocalDate, val goals: List<DayGoal>, val logs: List<FoodLog>)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val container: AppContainer) : ViewModel() {
    private val goals = container.goalRepository
    private val month = MutableStateFlow(YearMonth.from(container.today.value))
    private val filter = MutableStateFlow<Long?>(null)
    private val selected = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<CalendarState> = combine(
        month,
        filter,
        goals.activeGoals,
        goals.allStatuses,
        container.today,
    ) { month, filterId, activeGoals, statuses, today ->
        val byKey = statuses.associateBy { it.date to it.goalId }
        val shown = activeGoals.filter { filterId == null || it.id == filterId }

        fun statusOf(date: LocalDate, goalId: Long) = byKey[date to goalId]?.status ?: GoalStatus.UNMARKED

        val days = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            val applicable = if (date > today) emptyList() else shown.filter { it.activeFrom <= date }
            CalendarDay(date, DayColours.of(applicable.map { statusOf(date, it.id) }), date == today)
        }
        val streak = Streaks.compute(
            shown.map { StreakGoal(it.id, it.activeFrom, it.weeklyAllowance) },
            ::statusOf,
            today,
        )
        val countedGoals = if (filterId == null) shown.filter { it.avoidTag == Tags.ALCOHOL } else shown
        val missed = if (countedGoals.isEmpty()) {
            null
        } else {
            days.count { day -> countedGoals.any { statusOf(day.date, it.id) == GoalStatus.MISSED } }
        }
        CalendarState(month, days, activeGoals, filterId, streak, missed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState(month.value))

    val detail: StateFlow<DayDetail?> = selected.flatMapLatest { date ->
        if (date == null) {
            flowOf(null)
        } else {
            combine(
                goals.activeGoals,
                goals.statusesBetween(date, date),
                container.foodRepository.logsOn(date),
            ) { activeGoals, statuses, logs ->
                val byGoal = statuses.associateBy(DayGoalStatus::goalId)
                DayDetail(
                    date = date,
                    goals = activeGoals.filter { it.activeFrom <= date }.map { goal ->
                        val status = byGoal[goal.id]
                        DayGoal(goal, status?.status ?: GoalStatus.UNMARKED, status?.source == StatusSource.MANUAL)
                    },
                    logs = logs,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showMonth(offset: Long) {
        month.value = month.value.plusMonths(offset)
    }

    fun setFilter(goalId: Long?) {
        filter.value = goalId
    }

    fun select(date: LocalDate?) {
        selected.value = date
    }

    /** Null clears the manual override and hands the day back to the auto rule. */
    fun setManual(date: LocalDate, goalId: Long, status: GoalStatus?) = viewModelScope.launch {
        goals.setManual(date, goalId, status)
    }
}
