package dev.matthewmorris.cuttracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.matthewmorris.cuttracker.domain.BodyMetric
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
    val brand: String? = null,
    /**
     * Foods from Open Food Facts are kept per 100 g (or ml), the figure it gets right; the amount is
     * chosen when logging. Null for presets and quick-add foods, which are per serving.
     */
    val kcalPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    /** As listed on Open Food Facts, which is often wrong; offered as a shortcut, never assumed. */
    val servingG: Double? = null,
    val packG: Double? = null,
    @ColumnInfo(defaultValue = "0") val isLiquid: Boolean = false,
    /** The amount logged last time, offered first next time. */
    val lastAmountG: Double? = null,
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
    /** Set when the amount was chosen in grams (or ml), for foods kept per 100 g. */
    val grams: Double? = null,
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

/**
 * One measurement: a weight, a body-fat percentage or a lean mass. Health Connect stores these as
 * separate records with their own ids, so each record maps to exactly one row; a weigh-in on screen
 * is the readings taken within a few minutes of each other.
 */
@Entity(indices = [Index("hcRecordId", unique = true), Index("timestamp")])
data class BodyReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Instant,
    val metric: BodyMetric,
    val value: Double,
    val source: MeasurementSource,
    /** Package of the app that wrote it to Health Connect, e.g. Renpho Health. Null for manual. */
    val sourceApp: String? = null,
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
