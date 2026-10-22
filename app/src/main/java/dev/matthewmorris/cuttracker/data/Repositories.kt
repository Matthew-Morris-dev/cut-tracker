package dev.matthewmorris.cuttracker.data

import dev.matthewmorris.cuttracker.data.db.AppDatabase
import dev.matthewmorris.cuttracker.data.db.BodyReading
import dev.matthewmorris.cuttracker.data.db.FoodItem
import dev.matthewmorris.cuttracker.data.db.FoodLog
import dev.matthewmorris.cuttracker.data.db.Goal
import dev.matthewmorris.cuttracker.data.db.Targets
import dev.matthewmorris.cuttracker.data.food.FoodChoice
import dev.matthewmorris.cuttracker.data.food.FoodProduct
import dev.matthewmorris.cuttracker.data.food.OnlineFoodSource
import dev.matthewmorris.cuttracker.data.health.HealthConnectSync
import dev.matthewmorris.cuttracker.domain.BodyMetric
import dev.matthewmorris.cuttracker.domain.GoalStatus
import dev.matthewmorris.cuttracker.domain.MeasurementSource
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

sealed interface BarcodeLookup {
    /** A saved per-serving food (a preset or quick-add food): log it as it is. */
    data class Saved(val item: FoodItem) : BarcodeLookup

    /** A food kept per 100 g: choose the amount. */
    data class Choose(val choice: FoodChoice) : BarcodeLookup

    data class NotFound(val barcode: String) : BarcodeLookup
}

class FoodRepository(
    db: AppDatabase,
    private val resolver: GoalStatusResolver,
    private val clock: Clock,
    private val health: HealthConnectSync,
    private val online: OnlineFoodSource,
) {
    private val dao = db.foodDao()

    val presets = dao.presets()
    val favourites = dao.favourites()
    val recents = dao.recentLogs(limit = 10)

    fun logsOn(date: LocalDate) = dao.logsOn(date)

    suspend fun logItem(item: FoodItem, servings: Double = 1.0) = insertLog(
        FoodLog(
            date = LocalDate.now(clock),
            loggedAt = Instant.now(clock),
            foodItemId = item.id,
            name = item.name,
            kcal = (item.kcalPerServing * servings).roundToInt(),
            proteinG = item.proteinG * servings,
            servings = servings,
            tags = item.tags,
        ),
    )

    suspend fun relog(log: FoodLog) = insertLog(log.copy(id = 0, date = LocalDate.now(clock), loggedAt = Instant.now(clock)))

    /** With a [barcode] (a scan Open Food Facts didn't know) the food is saved so the next scan finds it. */
    suspend fun quickAdd(
        name: String,
        kcal: Int,
        proteinG: Double,
        tags: Set<String>,
        saveAsFavourite: Boolean,
        barcode: String? = null,
    ) {
        val itemId = if (saveAsFavourite || barcode != null) {
            dao.upsertItem(
                FoodItem(
                    name = name,
                    kcalPerServing = kcal,
                    proteinG = proteinG,
                    barcode = barcode,
                    tags = tags,
                    isFavourite = saveAsFavourite,
                ),
            )
        } else {
            null
        }
        insertLog(
            FoodLog(
                date = LocalDate.now(clock),
                loggedAt = Instant.now(clock),
                foodItemId = itemId,
                name = name,
                kcal = kcal,
                proteinG = proteinG,
                tags = tags,
            ),
        )
    }

    /** Your saved foods first, offline and instant; then Open Food Facts. Network errors are thrown. */
    suspend fun lookUpBarcode(barcode: String): BarcodeLookup {
        dao.itemByBarcode(barcode)?.let { item ->
            return FoodChoice.of(item)?.let(BarcodeLookup::Choose) ?: BarcodeLookup.Saved(item)
        }
        val product = online.byBarcode(barcode) ?: return BarcodeLookup.NotFound(barcode)
        return BarcodeLookup.Choose(FoodChoice.of(product))
    }

    suspend fun searchSaved(query: String): List<FoodItem> = dao.searchItems(query)

    suspend fun searchOnline(query: String): List<FoodProduct> = online.search(query)

    /**
     * What to show for a search result. The search index has no serving or pack sizes, so the product
     * is looked up by barcode for them; a food you've logged before comes back with your last amount.
     */
    suspend fun choiceFor(product: FoodProduct): FoodChoice {
        val barcode = product.barcode ?: return FoodChoice.of(product)
        dao.itemByBarcode(barcode)?.let { saved -> FoodChoice.of(saved)?.let { return it } }
        val details = try {
            online.details(barcode)
        } catch (e: IOException) {
            null
        } ?: return FoodChoice.of(product)
        return FoodChoice.of(
            product.copy(
                servingG = details.servingG ?: product.servingG,
                packG = details.packG ?: product.packG,
                isLiquid = details.isLiquid || product.isLiquid,
                suggestedTag = details.suggestedTag ?: product.suggestedTag,
            ),
        )
    }

    /** Saves the food (so recents, favourites and re-scans work offline) and logs [grams] of it. */
    suspend fun logAmount(choice: FoodChoice, grams: Double, tag: String?, favourite: Boolean) {
        val existing = choice.itemId?.let { dao.itemById(it) } ?: choice.barcode?.let { dao.itemByBarcode(it) }
        val servingG = choice.servingG ?: 100.0
        val item = FoodItem(
            id = existing?.id ?: 0,
            name = choice.name,
            brand = choice.brand,
            kcalPerServing = choice.kcalFor(servingG),
            proteinG = choice.proteinFor(servingG),
            servingLabel = existing?.servingLabel ?: "1 serving",
            barcode = choice.barcode,
            tags = setOfNotNull(tag),
            isFavourite = favourite,
            kcalPer100g = choice.kcalPer100g,
            proteinPer100g = choice.proteinPer100g,
            servingG = choice.servingG,
            packG = choice.packG,
            isLiquid = choice.isLiquid,
            lastAmountG = grams,
        )
        val inserted = dao.upsertItem(item)
        insertLog(
            FoodLog(
                date = LocalDate.now(clock),
                loggedAt = Instant.now(clock),
                foodItemId = existing?.id ?: inserted,
                name = choice.name,
                kcal = choice.kcalFor(grams),
                proteinG = choice.proteinFor(grams),
                tags = setOfNotNull(tag),
                grams = grams,
            ),
        )
    }

    suspend fun deleteLog(log: FoodLog) {
        dao.deleteLog(log)
        resolver.resolve(log.date, log.date)
        health.onFoodLogDeleted(log)
    }

    suspend fun saveItem(item: FoodItem) {
        dao.upsertItem(item)
    }

    suspend fun deleteItem(item: FoodItem) = dao.deleteItem(item)

    private suspend fun insertLog(log: FoodLog) {
        val id = dao.insertLog(log)
        resolver.resolve(log.date, log.date)
        health.onFoodLogged(log.copy(id = id))
    }
}

class GoalRepository(db: AppDatabase, private val resolver: GoalStatusResolver) {
    private val dao = db.goalDao()

    val activeGoals = dao.activeGoals()
    val allGoals = dao.allGoals()
    val allStatuses = dao.allStatuses()

    fun statusesBetween(from: LocalDate, to: LocalDate) = dao.statusesBetween(from, to)

    suspend fun add(goal: Goal) {
        dao.insert(goal)
        resolver.resolveAll()
    }

    suspend fun update(goal: Goal) {
        val before = dao.getGoal(goal.id)
        dao.update(goal)
        // Statuses depend on the tag and on which goals are active, not on the name.
        if (before?.avoidTag != goal.avoidTag || before.archived != goal.archived) {
            resolver.resolveAll()
        }
    }

    suspend fun setManual(date: LocalDate, goalId: Long, status: GoalStatus?) = resolver.setManual(date, goalId, status)
}

class TargetsRepository(db: AppDatabase, private val clock: Clock) {
    private val dao = db.targetsDao()

    val current = dao.latest()

    suspend fun save(targets: Targets) {
        dao.insert(targets.copy(id = 0, updatedAt = Instant.now(clock)))
    }
}

class BodyRepository(db: AppDatabase, private val clock: Clock) {
    private val dao = db.bodyDao()

    val readings = dao.all()

    suspend fun addManual(weightKg: Double, bodyFatPct: Double?) {
        val now = Instant.now(clock)
        dao.insert(
            listOfNotNull(
                BodyReading(timestamp = now, metric = BodyMetric.WEIGHT_KG, value = weightKg, source = MeasurementSource.MANUAL),
                bodyFatPct?.let {
                    BodyReading(timestamp = now, metric = BodyMetric.BODY_FAT_PCT, value = it, source = MeasurementSource.MANUAL)
                },
            ),
        )
    }

    suspend fun delete(readings: List<BodyReading>) = dao.delete(readings)
}
