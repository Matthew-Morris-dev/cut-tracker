package dev.matthewmorris.cuttracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matthewmorris.cuttracker.AppContainer
import dev.matthewmorris.cuttracker.data.db.FoodItem
import dev.matthewmorris.cuttracker.data.db.FoodLog
import dev.matthewmorris.cuttracker.data.db.Goal
import dev.matthewmorris.cuttracker.domain.GoalStatus
import dev.matthewmorris.cuttracker.domain.StatusSource
import dev.matthewmorris.cuttracker.ui.defaultTags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalToday(val goal: Goal, val status: GoalStatus, val manual: Boolean)

data class TodayState(
    val date: LocalDate,
    val logs: List<FoodLog> = emptyList(),
    val kcalEaten: Int = 0,
    val proteinEaten: Double = 0.0,
    val kcalTarget: Int = 0,
    val proteinTarget: Int = 0,
    val goals: List<GoalToday> = emptyList(),
)

data class AddSheetState(
    val presets: List<FoodItem> = emptyList(),
    val favourites: List<FoodItem> = emptyList(),
    val recents: List<FoodLog> = emptyList(),
    val tags: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val food = container.foodRepository
    private val goals = container.goalRepository

    val state: StateFlow<TodayState> = container.today.flatMapLatest { date ->
        combine(
            food.logsOn(date),
            container.targetsRepository.current,
            goals.activeGoals,
            goals.statusesBetween(date, date),
        ) { logs, targets, activeGoals, statuses ->
            val byGoal = statuses.associateBy { it.goalId }
            TodayState(
                date = date,
                logs = logs,
                kcalEaten = logs.sumOf { it.kcal },
                proteinEaten = logs.sumOf { it.proteinG },
                kcalTarget = targets?.kcalTarget ?: 0,
                proteinTarget = targets?.proteinTarget ?: 0,
                goals = activeGoals.filter { it.activeFrom <= date }.map { goal ->
                    val status = byGoal[goal.id]
                    GoalToday(goal, status?.status ?: GoalStatus.UNMARKED, status?.source == StatusSource.MANUAL)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState(container.today.value))

    val sheet: StateFlow<AddSheetState> = combine(
        food.presets,
        food.favourites,
        food.recents,
        goals.activeGoals.map { active -> (defaultTags + active.map { it.avoidTag }).distinct() },
    ) { presets, favourites, recents, tags ->
        AddSheetState(presets, favourites, recents, tags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddSheetState())

    fun logItem(item: FoodItem) = viewModelScope.launch { food.logItem(item) }

    fun relog(log: FoodLog) = viewModelScope.launch { food.relog(log) }

    fun quickAdd(name: String, kcal: Int, proteinG: Double, tag: String?, saveAsFavourite: Boolean) = viewModelScope.launch {
        food.quickAdd(name, kcal, proteinG, setOfNotNull(tag), saveAsFavourite)
    }

    fun deleteLog(log: FoodLog) = viewModelScope.launch { food.deleteLog(log) }

    fun saveItem(item: FoodItem) = viewModelScope.launch { food.saveItem(item) }

    fun deleteItem(item: FoodItem) = viewModelScope.launch { food.deleteItem(item) }

    /** Manual override for "I had a beer I didn't log": auto -> missed -> met -> back to auto. */
    fun cycleGoal(goal: GoalToday) = viewModelScope.launch {
        val next = when {
            !goal.manual -> GoalStatus.MISSED
            goal.status == GoalStatus.MISSED -> GoalStatus.MET
            else -> null
        }
        goals.setManual(state.value.date, goal.goal.id, next)
    }
}
