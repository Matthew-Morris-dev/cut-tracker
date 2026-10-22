package dev.matthewmorris.cuttracker.data.health

import android.util.Log
import androidx.room.withTransaction
import dev.matthewmorris.cuttracker.data.db.AppDatabase
import dev.matthewmorris.cuttracker.data.db.BodyReading
import dev.matthewmorris.cuttracker.data.db.FoodLog
import dev.matthewmorris.cuttracker.domain.BodyMetric
import dev.matthewmorris.cuttracker.domain.MeasurementSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class HealthStatus(
    val availability: HealthAvailability = HealthAvailability.UNAVAILABLE,
    val granted: Set<String> = emptySet(),
    val backgroundAvailable: Boolean = false,
    val lastSyncAt: Instant? = null,
    val syncing: Boolean = false,
    val lastSyncFailed: Boolean = false,
) {
    val connected: Boolean get() = granted.any { it in HealthPermissions.readBody }
    val backgroundGranted: Boolean get() = HealthPermissions.BACKGROUND in granted
    val canWriteNutrition: Boolean get() = HealthPermissions.writeNutrition in granted
}

/**
 * Two-way Health Connect sync: weigh-ins come in (one change token per metric), logged food goes out.
 *
 * Every step is safe to repeat. Readings are keyed by Health Connect record id and food by a client
 * id, so an interrupted sync simply runs again next time.
 */
class HealthConnectSync(
    private val source: HealthDataSource,
    private val store: HealthSyncStore,
    private val db: AppDatabase,
    private val clock: Clock,
    private val onBackgroundSyncChanged: (enabled: Boolean) -> Unit,
) {
    private val bodyDao = db.bodyDao()
    private val foodDao = db.foodDao()
    private val mutex = Mutex()
    private val _status = MutableStateFlow(HealthStatus(lastSyncAt = store.lastSyncAt))
    val status: StateFlow<HealthStatus> = _status.asStateFlow()

    /** Never throws: a failure is logged, shown on screen and retried on the next sync. */
    suspend fun sync(): Boolean = mutex.withLock {
        _status.update { it.copy(syncing = true) }
        var ok = false
        try {
            val current = refreshStatus()
            if (current.availability == HealthAvailability.AVAILABLE && current.granted.isNotEmpty()) {
                BodyMetric.entries.filter { HealthPermissions.read(it) in current.granted }.forEach { syncMetric(it) }
                if (current.canWriteNutrition) pushNutrition()
                store.lastSyncAt = Instant.now(clock)
            }
            ok = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect sync failed", e)
        } finally {
            _status.update { it.copy(syncing = false, lastSyncFailed = !ok, lastSyncAt = store.lastSyncAt) }
        }
        ok
    }

    suspend fun onFoodLogged(log: FoodLog) {
        val written = _status.value.canWriteNutrition && attempt { source.upsertNutrition(listOf(log.toNutritionEntry())) }
        if (!written) store.nutritionCatchUpNeeded = true
    }

    suspend fun onFoodLogDeleted(log: FoodLog) {
        // Without write permission nothing of ours can be in Health Connect to delete.
        if (!_status.value.canWriteNutrition) return
        val clientId = log.nutritionClientId()
        if (!attempt { source.deleteNutrition(listOf(clientId)) }) store.addPendingNutritionDelete(clientId)
    }

    private suspend fun refreshStatus(): HealthStatus {
        val availability = source.availability()
        val available = availability == HealthAvailability.AVAILABLE
        val granted = if (available) source.grantedPermissions() else emptySet()
        val background = available && source.backgroundReadAvailable()
        _status.update { it.copy(availability = availability, granted = granted, backgroundAvailable = background) }
        val current = _status.value
        onBackgroundSyncChanged(current.connected && background && current.backgroundGranted)
        return current
    }

    private suspend fun syncMetric(metric: BodyMetric) {
        var token = store.token(metric) ?: return importRecent(metric)
        do {
            val page = source.changes(token)
            if (page.tokenExpired) return importRecent(metric)
            db.withTransaction {
                page.changes.forEach { change ->
                    when (change) {
                        is HealthChange.Upsert -> bodyDao.upsertFromHealthConnect(listOf(change.reading.toRow()))
                        is HealthChange.Delete -> bodyDao.deleteByHealthConnectIds(listOf(change.id))
                    }
                }
            }
            token = page.nextToken
        } while (page.hasMore)
        store.setToken(metric, token)
    }

    /**
     * First sync, or a token that expired after 30 days unused. Take the new token before reading so
     * nothing written during the read is missed, then replace the window with what Health Connect has.
     */
    private suspend fun importRecent(metric: BodyMetric) {
        val token = source.changesToken(metric)
        val now = Instant.now(clock)
        val from = now.minus(IMPORT_WINDOW)
        val readings = source.read(metric, from, now)
        val ids = readings.map { it.id }.toSet()
        db.withTransaction {
            val gone = bodyDao.healthConnectIdsSince(metric, from).filterNot { it in ids }
            if (gone.isNotEmpty()) bodyDao.deleteByHealthConnectIds(gone)
            bodyDao.upsertFromHealthConnect(readings.map { it.toRow() })
        }
        store.setToken(metric, token)
    }

    private suspend fun pushNutrition() {
        if (store.nutritionCatchUpNeeded) {
            val logs = foodDao.logsSince(LocalDate.now(clock).minusDays(IMPORT_WINDOW.toDays()))
            source.upsertNutrition(logs.map { it.toNutritionEntry() })
            store.nutritionCatchUpNeeded = false
        }
        val pending = store.pendingNutritionDeletes
        if (pending.isNotEmpty()) {
            source.deleteNutrition(pending.toList())
            store.removePendingNutritionDeletes(pending)
        }
    }

    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Health Connect write failed", e)
        false
    }

    private fun HealthReading.toRow() = BodyReading(
        timestamp = time,
        metric = metric,
        value = value,
        source = MeasurementSource.HEALTH_CONNECT,
        sourceApp = sourceApp,
        hcRecordId = id,
    )

    companion object {
        private const val TAG = "HealthConnectSync"

        /** How far back a first sync (or a re-import after an expired token) reads. */
        val IMPORT_WINDOW: Duration = Duration.ofDays(30)
    }
}

fun FoodLog.nutritionClientId() = "foodlog-$id"

/** Health Connect needs an interval, so a logged food takes up the minute it was logged in. */
fun FoodLog.toNutritionEntry() = NutritionEntry(
    clientId = nutritionClientId(),
    start = loggedAt,
    end = loggedAt.plusSeconds(60),
    name = name,
    kcal = kcal.toDouble(),
    proteinG = proteinG,
)
