package dev.matthewmorris.cuttracker.data.health

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.matthewmorris.cuttracker.data.db.AppDatabase
import dev.matthewmorris.cuttracker.data.db.FoodLog
import dev.matthewmorris.cuttracker.domain.BodyMetric
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class HealthConnectSyncTest {
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val renpho = "com.renpho.health"

    private lateinit var db: AppDatabase
    private lateinit var store: HealthSyncStore
    private lateinit var source: FakeHealthDataSource
    private lateinit var sync: HealthConnectSync
    private var backgroundSyncEnabled: Boolean? = null

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = HealthSyncStore(context)
        source = FakeHealthDataSource()
        sync = HealthConnectSync(source, store, db, clock) { backgroundSyncEnabled = it }
    }

    @After fun tearDown() = db.close()

    private fun weight(id: String, daysAgo: Long, kg: Double) =
        HealthReading(id, BodyMetric.WEIGHT_KG, now.minus(Duration.ofDays(daysAgo)), kg, renpho)

    private suspend fun stored() = db.bodyDao().getAll().associate { it.hcRecordId to it.value }

    @Test fun `first sync imports the last 30 days only`() = runTest {
        source.put(weight("recent", 2, 84.2))
        source.put(weight("old", 40, 86.0))

        assertTrue(sync.sync())

        assertEquals(mapOf("recent" to 84.2), stored())
        assertEquals(renpho, db.bodyDao().getAll().single().sourceApp)
    }

    @Test fun `later syncs apply new readings and deletions from the change token`() = runTest {
        source.put(weight("a", 2, 84.2))
        sync.sync()
        val readsAfterFirstSync = source.reads

        source.put(weight("b", 1, 84.0))
        source.remove("a")
        sync.sync()

        assertEquals(mapOf("b" to 84.0), stored())
        assertEquals("changes, not a full re-read", readsAfterFirstSync, source.reads)
    }

    @Test fun `changes spread over several pages are all applied`() = runTest {
        sync.sync()
        source.pageSize = 1
        source.put(weight("a", 3, 84.4))
        source.put(weight("b", 2, 84.2))
        source.put(weight("c", 1, 84.0))

        sync.sync()

        assertEquals(setOf("a", "b", "c"), stored().keys)
    }

    @Test fun `an edited record replaces its row instead of adding one`() = runTest {
        source.put(weight("a", 1, 84.2))
        sync.sync()

        source.put(weight("a", 1, 83.9))
        sync.sync()

        assertEquals(mapOf("a" to 83.9), stored())
    }

    @Test fun `an expired token re-imports and drops readings deleted meanwhile`() = runTest {
        source.put(weight("kept", 3, 84.2))
        source.put(weight("deleted", 2, 84.0))
        sync.sync()

        source.removeSilently("deleted")
        source.put(weight("new", 1, 83.8))
        source.tokensExpired = true
        sync.sync()

        assertEquals(mapOf("kept" to 84.2, "new" to 83.8), stored())
    }

    @Test fun `a metric without read permission is left alone`() = runTest {
        source.granted = setOf(HealthPermissions.read(BodyMetric.WEIGHT_KG))
        source.put(weight("w", 1, 84.2))
        source.put(HealthReading("fat", BodyMetric.BODY_FAT_PCT, now.minusSeconds(3600), 25.0, renpho))

        sync.sync()

        assertEquals(setOf("w"), stored().keys)
    }

    @Test fun `nothing happens when Health Connect is unavailable`() = runTest {
        source.availability = HealthAvailability.UNAVAILABLE
        source.put(weight("a", 1, 84.2))

        assertTrue(sync.sync())

        assertTrue(stored().isEmpty())
        assertEquals(false, backgroundSyncEnabled)
    }

    @Test fun `background sync is scheduled only while background read is granted`() = runTest {
        sync.sync()
        assertEquals(true, backgroundSyncEnabled)

        source.granted = source.granted - HealthPermissions.BACKGROUND
        sync.sync()
        assertEquals(false, backgroundSyncEnabled)
    }

    @Test fun `the first sync with write permission sends the last 30 days of logged food once`() = runTest {
        val today = LocalDate.now(clock)
        val recent = log(today.minusDays(3))
        val old = log(today.minusDays(40))

        sync.sync()
        sync.sync()

        assertEquals(setOf("foodlog-${recent.id}"), source.nutrition.keys)
        assertFalse(source.nutrition.containsKey("foodlog-${old.id}"))
        assertEquals(1, source.nutritionUpserts)
    }

    @Test fun `logged food is written straight away, with a one-minute interval`() = runTest {
        sync.sync()
        val logged = log(LocalDate.now(clock))

        sync.onFoodLogged(logged)

        val entry = source.nutrition.getValue("foodlog-${logged.id}")
        assertEquals(Duration.ofMinutes(1), Duration.between(entry.start, entry.end))
        assertEquals(550.0, entry.kcal, 1e-9)
    }

    @Test fun `a food write that fails is caught up on the next sync`() = runTest {
        sync.sync()
        source.failNutrition = true
        val logged = log(LocalDate.now(clock))

        sync.onFoodLogged(logged)
        assertTrue(store.nutritionCatchUpNeeded)

        source.failNutrition = false
        sync.sync()
        assertTrue(source.nutrition.containsKey("foodlog-${logged.id}"))
    }

    @Test fun `a food delete that fails is retried on the next sync`() = runTest {
        val logged = log(LocalDate.now(clock))
        sync.sync()
        assertTrue(source.nutrition.containsKey("foodlog-${logged.id}"))

        source.failNutrition = true
        sync.onFoodLogDeleted(logged)
        assertEquals(setOf("foodlog-${logged.id}"), store.pendingNutritionDeletes)

        source.failNutrition = false
        sync.sync()
        assertFalse(source.nutrition.containsKey("foodlog-${logged.id}"))
        assertTrue(store.pendingNutritionDeletes.isEmpty())
    }

    private suspend fun log(date: LocalDate): FoodLog {
        val log = FoodLog(
            date = date,
            loggedAt = date.atTime(12, 0).toInstant(ZoneOffset.UTC),
            foodItemId = null,
            name = "Chicken wrap",
            kcal = 550,
            proteinG = 40.0,
        )
        return log.copy(id = db.foodDao().insertLog(log))
    }
}
