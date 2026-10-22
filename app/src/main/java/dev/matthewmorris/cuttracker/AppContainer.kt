package dev.matthewmorris.cuttracker

import android.content.Context
import dev.matthewmorris.cuttracker.data.BodyRepository
import dev.matthewmorris.cuttracker.data.FoodRepository
import dev.matthewmorris.cuttracker.data.GoalRepository
import dev.matthewmorris.cuttracker.data.GoalStatusResolver
import dev.matthewmorris.cuttracker.data.TargetsRepository
import dev.matthewmorris.cuttracker.data.db.AppDatabase
import dev.matthewmorris.cuttracker.data.food.OpenFoodFactsClient
import dev.matthewmorris.cuttracker.data.health.HealthConnectSource
import dev.matthewmorris.cuttracker.data.health.HealthConnectSync
import dev.matthewmorris.cuttracker.data.health.HealthSyncStore
import dev.matthewmorris.cuttracker.data.health.HealthSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate

class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    private val db = AppDatabase.build(context)
    private val resolver = GoalStatusResolver(db) { LocalDate.now(clock) }

    val healthSource = HealthConnectSource(context)
    val healthSync = HealthConnectSync(healthSource, HealthSyncStore(context), db, clock) { enabled ->
        HealthSyncWorker.schedule(context, enabled)
    }

    private val openFoodFacts = OpenFoodFactsClient(
        userAgent = "CutTracker/${BuildConfig.VERSION_NAME} (https://github.com/Matthew-Morris-dev/cut-tracker)",
    )

    val foodRepository = FoodRepository(db, resolver, clock, healthSync, openFoodFacts)
    val goalRepository = GoalRepository(db, resolver)
    val targetsRepository = TargetsRepository(db, clock)
    val bodyRepository = BodyRepository(db, clock)

    private val _today = MutableStateFlow(LocalDate.now(clock))

    /** Today's date, refreshed each time the app comes to the foreground. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    suspend fun onForeground() {
        _today.value = LocalDate.now(clock)
        resolver.resolveAll()
        healthSync.sync()
    }
}
