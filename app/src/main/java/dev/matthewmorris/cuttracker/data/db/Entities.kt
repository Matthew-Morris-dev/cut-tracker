package dev.matthewmorris.cuttracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.matthewmorris.cuttracker.domain.GoalStatus
import dev.matthewmorris.cuttracker.domain.MeasurementSource
import dev.matthewmorris.cuttracker.domain.Phase
import dev.matthewmorris.cuttracker.domain.StatusSource
import java.time.Instant
import java.time.LocalDate

/** Presets, custom foods and (later) scanned foods. Presets are the one-tap chips on the add sheet. */
@Entity
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcalPerServing: Int,
    val proteinG: Double,
    val servingLabel: String = "1 serving",
    val barcode: String? = null,
    val tags: Set<String> = emptySet(),
    val isFavourite: Boolean = false,
    val isPreset: Boolean = false,
)

/**
 * A logged entry. Values are copied at log time so editing a food doesn't rewrite history.
 * [kcal] and [proteinG] are totals for the entry (already multiplied by [servings]).
 */
@Entity(indices = [Index("date")])
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val loggedAt: Instant,
    val foodItemId: Long?,
    val name: String,
    val kcal: Int,
    val proteinG: Double,
    val servings: Double = 1.0,
    val tags: Set<String> = emptySet(),
)

/** "No alcohol" is avoidTag `alcohol`. */
@Entity
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avoidTag: String,
    val weeklyAllowance: Int? = null,
    val activeFrom: LocalDate,
    val archived: Boolean = false,
)

/** No row means unmarked. A MANUAL row always beats the auto rule. */
@Entity(primaryKeys = ["date", "goalId"])
data class DayGoalStatus(
    val date: LocalDate,
    val goalId: Long,
    val status: GoalStatus,
    val source: StatusSource,
)

@Entity(indices = [Index("hcRecordId", unique = true), Index("timestamp")])
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Instant,
    val weightKg: Double,
    val bodyFatPct: Double? = null,
    val leanMassKg: Double? = null,
    val source: MeasurementSource,
    val hcRecordId: String? = null,
)

/** One row per change, so target history is kept. The latest row is current. */
@Entity
data class Targets(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kcalTarget: Int,
    val proteinTarget: Int,
    val goalWeightKg: Double,
    val targetRateKgPerWeek: Double,
    val phase: Phase,
    val updatedAt: Instant,
)
