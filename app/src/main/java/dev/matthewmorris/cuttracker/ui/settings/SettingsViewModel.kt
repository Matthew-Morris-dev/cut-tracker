package dev.matthewmorris.cuttracker.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matthewmorris.cuttracker.AppContainer
import dev.matthewmorris.cuttracker.data.db.Goal
import dev.matthewmorris.cuttracker.data.db.Targets
import dev.matthewmorris.cuttracker.data.health.HealthStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val goals = container.goalRepository

    val targets: StateFlow<Targets?> = container.targetsRepository.current
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val allGoals: StateFlow<List<Goal>> = goals.allGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveTargets(targets: Targets) = viewModelScope.launch { container.targetsRepository.save(targets) }

    /** A new goal starts today; it doesn't rewrite the calendar before it existed. */
    fun addGoal(name: String, avoidTag: String, weeklyAllowance: Int?) = viewModelScope.launch {
        goals.add(Goal(name = name, avoidTag = avoidTag, weeklyAllowance = weeklyAllowance, activeFrom = container.today.value))
    }

    fun updateGoal(goal: Goal) = viewModelScope.launch { goals.update(goal) }

    val health: StateFlow<HealthStatus> = container.healthSync.status

    fun syncHealth() = viewModelScope.launch { container.healthSync.sync() }

    /** Emulator debug builds only: puts two weeks of weigh-ins into Health Connect, then syncs them back in. */
    fun writeTestWeighIns() = viewModelScope.launch {
        try {
            container.healthSource.writeTestWeighIns(container.clock)
        } catch (e: Exception) {
            Log.w("SettingsViewModel", "Couldn't write test weigh-ins", e)
        }
        container.healthSync.sync()
    }
}
