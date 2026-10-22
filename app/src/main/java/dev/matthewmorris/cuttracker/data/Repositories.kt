package dev.matthewmorris.cuttracker.data

import dev.matthewmorris.cuttracker.data.db.AppDatabase
import dev.matthewmorris.cuttracker.data.db.BodyMeasurement
import dev.matthewmorris.cuttracker.data.db.FoodItem
import dev.matthewmorris.cuttracker.data.db.FoodLog
import dev.matthewmorris.cuttracker.data.db.Goal
import dev.matthewmorris.cuttracker.data.db.Targets
import dev.matthewmorris.cuttracker.domain.GoalStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

class FoodRepository(db: AppDatabase, private val resolver: GoalStatusResolver, private val clock: Clock) {
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

    suspend fun quickAdd(name: String, kcal: Int, proteinG: Double, tags: Set<String>, saveAsFavourite: Boolean) {
        val itemId = if (saveAsFavourite) {
            dao.upsertItem(FoodItem(name = name, kcalPerServing = kcal, proteinG = proteinG, tags = tags, isFavourite = true))
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

    suspend fun deleteLog(log: FoodLog) {
        dao.deleteLog(log)
        resolver.resolve(log.date, log.date)
    }

    suspend fun saveItem(item: FoodItem) {
        dao.upsertItem(item)
    }

    suspend fun deleteItem(item: FoodItem) = dao.deleteItem(item)

    private suspend fun insertLog(log: FoodLog) {
        dao.insertLog(log)
        resolver.resolve(log.date, log.date)
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

class BodyRepository(db: AppDatabase) {
    private val dao = db.bodyDao()

    val measurements = dao.all()

    suspend fun add(measurement: BodyMeasurement) = dao.insert(measurement)

    suspend fun delete(measurement: BodyMeasurement) = dao.delete(measurement)
}
