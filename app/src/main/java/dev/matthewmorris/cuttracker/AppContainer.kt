package dev.matthewmorris.cuttracker

import android.content.Context
import dev.matthewmorris.cuttracker.data.BodyRepository
import dev.matthewmorris.cuttracker.data.FoodRepository
import dev.matthewmorris.cuttracker.data.GoalRepository
import dev.matthewmorris.cuttracker.data.GoalStatusResolver
import dev.matthewmorris.cuttracker.data.TargetsRepository
import dev.matthewmorris.cuttracker.data.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate

class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    private val db = AppDatabase.build(context)
    private val resolver = GoalStatusResolver(db) { LocalDate.now(clock) }

    val foodRepository = FoodRepository(db, resolver, clock)
    val goalRepository = GoalRepository(db, resolver)
    val targetsRepository = TargetsRepository(db, clock)
    val bodyRepository = BodyRepository(db)

    private val _today = MutableStateFlow(LocalDate.now(clock))

    /** Today's date, refreshed each time the app comes to the foreground. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    suspend fun onForeground() {
        _today.value = LocalDate.now(clock)
        resolver.resolveAll()
    }
}
