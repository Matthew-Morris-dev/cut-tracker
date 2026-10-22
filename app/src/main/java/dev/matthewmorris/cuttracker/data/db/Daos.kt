package dev.matthewmorris.cuttracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import dev.matthewmorris.cuttracker.domain.BodyMetric
import dev.matthewmorris.cuttracker.domain.GoalStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

data class LogTags(val date: LocalDate, val tags: Set<String>)

@Dao
interface FoodDao {
    @Query("SELECT * FROM FoodItem WHERE isPreset = 1 ORDER BY id")
    fun presets(): Flow<List<FoodItem>>

    @Query("SELECT * FROM FoodItem WHERE isFavourite = 1 AND isPreset = 0 ORDER BY name")
    fun favourites(): Flow<List<FoodItem>>

    @Upsert
    suspend fun upsertItem(item: FoodItem): Long

    @Query("SELECT * FROM FoodItem WHERE id = :id")
    suspend fun itemById(id: Long): FoodItem?

    @Query("SELECT * FROM FoodItem WHERE barcode = :barcode LIMIT 1")
    suspend fun itemByBarcode(barcode: String): FoodItem?

    /** Your own foods matching a search, favourites first. */
    @Query(
        """
        SELECT * FROM FoodItem
        WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%'
        ORDER BY isFavourite DESC, isPreset DESC, name LIMIT 20
        """,
    )
    suspend fun searchItems(query: String): List<FoodItem>

    @Delete
    suspend fun deleteItem(item: FoodItem)

    @Insert
    suspend fun insertLog(log: FoodLog): Long

    @Delete
    suspend fun deleteLog(log: FoodLog)

    @Query("SELECT * FROM FoodLog WHERE date = :date ORDER BY loggedAt DESC")
    fun logsOn(date: LocalDate): Flow<List<FoodLog>>

    /** Most recent entry for each distinct food, newest first. */
    @Query(
        """
        SELECT * FROM FoodLog WHERE id IN (
            SELECT MAX(id) FROM FoodLog GROUP BY name, kcal, proteinG, tags
        ) ORDER BY loggedAt DESC LIMIT :limit
        """,
    )
    fun recentLogs(limit: Int): Flow<List<FoodLog>>

    @Query("SELECT date, tags FROM FoodLog WHERE date BETWEEN :from AND :to")
    suspend fun logTagsBetween(from: LocalDate, to: LocalDate): List<LogTags>

    @Query("SELECT * FROM FoodLog WHERE date >= :from")
    suspend fun logsSince(from: LocalDate): List<FoodLog>

    @Query("SELECT MIN(date) FROM FoodLog")
    suspend fun earliestLogDate(): LocalDate?
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM Goal WHERE archived = 0 ORDER BY id")
    fun activeGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM Goal ORDER BY archived, id")
    fun allGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM Goal WHERE archived = 0 ORDER BY id")
    suspend fun getActiveGoals(): List<Goal>

    @Query("SELECT * FROM Goal WHERE id = :id")
    suspend fun getGoal(id: Long): Goal?

    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Query("SELECT MIN(activeFrom) FROM Goal WHERE archived = 0")
    suspend fun earliestActiveFrom(): LocalDate?

    @Query("SELECT * FROM DayGoalStatus")
    fun allStatuses(): Flow<List<DayGoalStatus>>

    @Query("SELECT * FROM DayGoalStatus WHERE date BETWEEN :from AND :to")
    fun statusesBetween(from: LocalDate, to: LocalDate): Flow<List<DayGoalStatus>>

    @Query("SELECT * FROM DayGoalStatus WHERE date BETWEEN :from AND :to")
    suspend fun getStatusesBetween(from: LocalDate, to: LocalDate): List<DayGoalStatus>

    @Upsert
    suspend fun upsertStatus(status: DayGoalStatus)

    @Query("DELETE FROM DayGoalStatus WHERE date = :date AND goalId = :goalId")
    suspend fun deleteStatus(date: LocalDate, goalId: Long)

    @Query("SELECT COUNT(*) FROM DayGoalStatus WHERE goalId = :goalId AND status = :status AND date BETWEEN :from AND :to")
    fun countStatus(goalId: Long, status: GoalStatus, from: LocalDate, to: LocalDate): Flow<Int>
}

@Dao
interface TargetsDao {
    @Query("SELECT * FROM Targets ORDER BY updatedAt DESC, id DESC LIMIT 1")
    fun latest(): Flow<Targets?>

    @Insert
    suspend fun insert(targets: Targets): Long
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM BodyReading ORDER BY timestamp DESC")
    fun all(): Flow<List<BodyReading>>

    @Query("SELECT * FROM BodyReading ORDER BY timestamp DESC")
    suspend fun getAll(): List<BodyReading>

    @Insert
    suspend fun insert(readings: List<BodyReading>)

    /** Health Connect readings, keyed by their record id: a record seen again replaces its row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromHealthConnect(readings: List<BodyReading>)

    @Query("SELECT hcRecordId FROM BodyReading WHERE metric = :metric AND hcRecordId IS NOT NULL AND timestamp >= :from")
    suspend fun healthConnectIdsSince(metric: BodyMetric, from: Instant): List<String>

    @Query("DELETE FROM BodyReading WHERE hcRecordId IN (:ids)")
    suspend fun deleteByHealthConnectIds(ids: List<String>)

    @Delete
    suspend fun delete(readings: List<BodyReading>)
}
