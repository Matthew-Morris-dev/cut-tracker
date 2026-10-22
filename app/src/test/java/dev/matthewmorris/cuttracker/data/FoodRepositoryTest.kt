package dev.matthewmorris.cuttracker.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.matthewmorris.cuttracker.data.db.AppDatabase
import dev.matthewmorris.cuttracker.data.food.FoodChoice
import dev.matthewmorris.cuttracker.data.food.FoodProduct
import dev.matthewmorris.cuttracker.data.food.OnlineFoodSource
import dev.matthewmorris.cuttracker.data.food.ProductDetails
import dev.matthewmorris.cuttracker.data.health.FakeHealthDataSource
import dev.matthewmorris.cuttracker.data.health.HealthConnectSync
import dev.matthewmorris.cuttracker.data.health.HealthSyncStore
import dev.matthewmorris.cuttracker.domain.Tags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FoodRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC)
    private val mars = FoodProduct("5000159407236", "Mars", "Mars", 450.0, 4.04, 100.0, 51.0, false, Tags.TREAT)

    private lateinit var db: AppDatabase
    private lateinit var online: FakeOnline
    private lateinit var repository: FoodRepository

    private class FakeOnline : OnlineFoodSource {
        val products = mutableMapOf<String, FoodProduct>()
        val details = mutableMapOf<String, ProductDetails>()
        var offline = false
        var lookups = 0

        override suspend fun byBarcode(barcode: String): FoodProduct? {
            lookups++
            if (offline) throw IOException("offline")
            return products[barcode]
        }

        override suspend fun details(barcode: String): ProductDetails? {
            if (offline) throw IOException("offline")
            return details[barcode]
        }

        override suspend fun search(query: String): List<FoodProduct> = products.values.filter { query in it.name }
    }

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        online = FakeOnline()
        val health = HealthConnectSync(FakeHealthDataSource(), HealthSyncStore(context), db, clock) {}
        repository = FoodRepository(db, GoalStatusResolver(db) { LocalDate.now(clock) }, clock, health, online)
    }

    @After fun tearDown() = db.close()

    @Test fun `logging a scanned food saves it, so the next scan needs no network`() = runTest {
        online.products[mars.barcode!!] = mars
        val first = repository.lookUpBarcode(mars.barcode!!) as BarcodeLookup.Choose

        repository.logAmount(first.choice, grams = 51.0, tag = Tags.TREAT, favourite = false)
        online.offline = true
        val second = repository.lookUpBarcode(mars.barcode!!) as BarcodeLookup.Choose

        assertEquals(51.0, second.choice.lastAmountG!!, 1e-9)
        assertEquals(1, online.lookups)
        val log = db.foodDao().logsOn(LocalDate.now(clock)).first().single()
        assertEquals(230, log.kcal)
        assertEquals(51.0, log.grams!!, 1e-9)
        assertEquals(setOf(Tags.TREAT), log.tags)
    }

    @Test fun `logging the same food again updates it rather than saving a copy`() = runTest {
        repository.logAmount(FoodChoice.of(mars), 51.0, Tags.TREAT, favourite = false)
        val saved = (repository.lookUpBarcode(mars.barcode!!) as BarcodeLookup.Choose).choice

        repository.logAmount(saved, 25.5, Tags.TREAT, favourite = true)

        val items = db.foodDao().searchItems("Mars")
        assertEquals(1, items.size)
        assertEquals(25.5, items.single().lastAmountG!!, 1e-9)
        assertTrue(items.single().isFavourite)
    }

    @Test fun `an unknown barcode added by quick add is found locally next time`() = runTest {
        assertEquals(BarcodeLookup.NotFound("123"), repository.lookUpBarcode("123"))

        repository.quickAdd("Market flapjack", 380, 5.0, setOf(Tags.TREAT), saveAsFavourite = false, barcode = "123")

        val found = repository.lookUpBarcode("123") as BarcodeLookup.Saved
        assertEquals("Market flapjack", found.item.name)
    }

    @Test fun `a search result gets serving, pack and tag from its product page even without calories there`() = runTest {
        // As seen live: the product page had lost its calories but kept everything else.
        online.details[mars.barcode!!] = ProductDetails(servingG = 39.4, packG = 157.6, isLiquid = false, suggestedTag = Tags.TREAT)

        val choice = repository.choiceFor(mars.copy(servingG = null, packG = null, suggestedTag = null))

        assertEquals(450.0, choice.kcalPer100g, 1e-9)
        assertEquals(39.4, choice.servingG!!, 1e-9)
        assertEquals(157.6, choice.packG!!, 1e-9)
        assertEquals(Tags.TREAT, choice.tag)
    }

    @Test fun `a search result still opens when its details can't be fetched`() = runTest {
        online.offline = true

        val choice = repository.choiceFor(mars.copy(servingG = null))

        assertEquals("Mars", choice.name)
        assertEquals(450.0, choice.kcalPer100g, 1e-9)
    }
}
