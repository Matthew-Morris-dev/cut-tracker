package dev.matthewmorris.cuttracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matthewmorris.cuttracker.AppContainer
import dev.matthewmorris.cuttracker.data.BarcodeLookup
import dev.matthewmorris.cuttracker.data.db.FoodItem
import dev.matthewmorris.cuttracker.data.db.FoodLog
import dev.matthewmorris.cuttracker.data.db.Goal
import dev.matthewmorris.cuttracker.data.food.FoodChoice
import dev.matthewmorris.cuttracker.data.food.FoodProduct
import dev.matthewmorris.cuttracker.data.food.RateLimitedException
import dev.matthewmorris.cuttracker.domain.GoalStatus
import dev.matthewmorris.cuttracker.domain.StatusSource
import dev.matthewmorris.cuttracker.ui.defaultTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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

sealed interface OnlineSearch {
    data object NotRun : OnlineSearch

    data object Searching : OnlineSearch

    data class Done(val results: List<FoodProduct>) : OnlineSearch

    data class Failed(val problem: LookupProblem) : OnlineSearch
}

enum class LookupProblem { NO_CONNECTION, TOO_MANY_REQUESTS }

/** Saved foods match as you type; Open Food Facts is only asked when you press search. */
data class FoodSearchState(
    val query: String = "",
    val saved: List<FoodItem> = emptyList(),
    val online: OnlineSearch = OnlineSearch.NotRun,
)

sealed interface ScanState {
    data object Idle : ScanState

    /** Play services is downloading the scanner, which happens before the first scan on a sideloaded app. */
    data object PreparingScanner : ScanState

    data object LookingUp : ScanState

    data class NotFound(val barcode: String) : ScanState

    /** [problem] is null when the scanner itself couldn't open. */
    data class Failed(val problem: LookupProblem?) : ScanState
}

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

    private val query = MutableStateFlow("")
    private val onlineSearch = MutableStateFlow<OnlineSearch>(OnlineSearch.NotRun)
    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    private val _choice = MutableStateFlow<FoodChoice?>(null)
    private val _sheetDone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val search: StateFlow<FoodSearchState> = combine(
        query,
        query.mapLatest { text -> if (text.isBlank()) emptyList() else food.searchSaved(text.trim()) },
        onlineSearch,
    ) { text, saved, online -> FoodSearchState(text, saved, online) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodSearchState())

    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    /** The food on the amount screen, if it's open. */
    val choice: StateFlow<FoodChoice?> = _choice.asStateFlow()

    /** Something was logged from the sheet without a tap that closes it (a scan or the amount screen). */
    val sheetDone: SharedFlow<Unit> = _sheetDone.asSharedFlow()

    fun setQuery(text: String) {
        query.value = text
        onlineSearch.value = OnlineSearch.NotRun
    }

    fun searchOnline() {
        val text = query.value.trim()
        if (text.length < 2 || onlineSearch.value == OnlineSearch.Searching) return
        onlineSearch.value = OnlineSearch.Searching
        viewModelScope.launch {
            val result = lookUp(onProblem = OnlineSearch::Failed) { OnlineSearch.Done(food.searchOnline(text)) }
            // Ignore a result for a query that has since been edited.
            if (query.value.trim() == text) onlineSearch.value = result
        }
    }

    /** Opens the amount screen straight away, then fills in serving and pack sizes once they arrive. */
    fun choose(product: FoodProduct) {
        val shown = FoodChoice.of(product)
        _choice.value = shown
        viewModelScope.launch {
            val full = food.choiceFor(product)
            if (_choice.value == shown) _choice.value = full
        }
    }

    /** A saved food kept per 100 g opens the amount screen; anything else is logged as it is. */
    fun pick(item: FoodItem) {
        val choice = FoodChoice.of(item)
        if (choice != null) {
            _choice.value = choice
        } else {
            logItem(item)
            _sheetDone.tryEmit(Unit)
        }
    }

    fun dismissChoice() {
        _choice.value = null
    }

    fun logChoice(choice: FoodChoice, grams: Double, tag: String?, favourite: Boolean) {
        _choice.value = null
        viewModelScope.launch { food.logAmount(choice, grams, tag, favourite) }
        _sheetDone.tryEmit(Unit)
    }

    fun onBarcode(barcode: String) {
        _scan.value = ScanState.LookingUp
        viewModelScope.launch {
            _scan.value = lookUp(onProblem = ScanState::Failed) {
                when (val found = food.lookUpBarcode(barcode)) {
                    is BarcodeLookup.Saved -> {
                        food.logItem(found.item)
                        _sheetDone.tryEmit(Unit)
                        ScanState.Idle
                    }
                    is BarcodeLookup.Choose -> {
                        _choice.value = found.choice
                        ScanState.Idle
                    }
                    is BarcodeLookup.NotFound -> ScanState.NotFound(found.barcode)
                }
            }
        }
    }

    fun onScannerPreparing() {
        _scan.value = ScanState.PreparingScanner
    }

    fun onScannerReady() {
        _scan.value = ScanState.Idle
    }

    fun onScannerFailed() {
        _scan.value = ScanState.Failed(null)
    }

    /** Called when the sheet closes, so it opens fresh next time. */
    fun resetSheet() {
        query.value = ""
        onlineSearch.value = OnlineSearch.NotRun
        _scan.value = ScanState.Idle
        _choice.value = null
    }

    private suspend fun <T> lookUp(onProblem: (LookupProblem) -> T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RateLimitedException) {
        onProblem(LookupProblem.TOO_MANY_REQUESTS)
    } catch (e: Exception) {
        // Offline, a timeout, or a reply that couldn't be read: all mean "try again later".
        onProblem(LookupProblem.NO_CONNECTION)
    }

    fun logItem(item: FoodItem) = viewModelScope.launch { food.logItem(item) }

    fun relog(log: FoodLog) = viewModelScope.launch { food.relog(log) }

    fun quickAdd(name: String, kcal: Int, proteinG: Double, tag: String?, saveAsFavourite: Boolean, barcode: String? = null) =
        viewModelScope.launch { food.quickAdd(name, kcal, proteinG, setOfNotNull(tag), saveAsFavourite, barcode) }

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
