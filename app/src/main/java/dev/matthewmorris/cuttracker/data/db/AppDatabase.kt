package dev.matthewmorris.cuttracker.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.matthewmorris.cuttracker.domain.Phase
import dev.matthewmorris.cuttracker.domain.Tags
import java.time.LocalDate

@Database(
    entities = [FoodItem::class, FoodLog::class, Goal::class, DayGoalStatus::class, BodyReading::class, Targets::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun goalDao(): GoalDao
    abstract fun targetsDao(): TargetsDao
    abstract fun bodyDao(): BodyDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cut-tracker.db")
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** First-run data: treat and alcohol presets, the two default goals and phase 1 targets. */
private object SeedCallback : RoomDatabase.Callback() {
    private data class Preset(val name: String, val kcal: Int, val protein: Double, val serving: String, val tag: String)

    private val presets = listOf(
        Preset("Pint of beer", 225, 2.0, "1 pint", Tags.ALCOHOL),
        Preset("Large glass of wine", 200, 0.0, "250 ml", Tags.ALCOHOL),
        Preset("Spirit and mixer", 125, 0.0, "1 drink", Tags.ALCOHOL),
        Preset("Chocolate bar", 250, 3.0, "1 bar", Tags.TREAT),
        Preset("Bag of crisps", 155, 2.0, "1 bag", Tags.TREAT),
    )

    override fun onCreate(db: SupportSQLiteDatabase) {
        presets.forEach { p ->
            db.insert(
                "FoodItem",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("name", p.name)
                    put("kcalPerServing", p.kcal)
                    put("proteinG", p.protein)
                    put("servingLabel", p.serving)
                    put("tags", p.tag)
                    put("isFavourite", 0)
                    put("isPreset", 1)
                },
            )
        }
        val today = LocalDate.now().toEpochDay()
        listOf("No alcohol" to Tags.ALCOHOL, "No sugar or treats" to Tags.TREAT).forEach { (name, tag) ->
            db.insert(
                "Goal",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("name", name)
                    put("avoidTag", tag)
                    put("activeFrom", today)
                    put("archived", 0)
                },
            )
        }
        db.insert(
            "Targets",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("kcalTarget", 2200)
                put("proteinTarget", 160)
                put("goalWeightKg", 72.0)
                put("targetRateKgPerWeek", 0.6)
                put("phase", Phase.CUT.name)
                put("updatedAt", System.currentTimeMillis())
            },
        )
    }
}

/** v2 splits each BodyMeasurement row into one BodyReading per metric it holds. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `BodyReading` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `metric` TEXT NOT NULL, `value` REAL NOT NULL, `source` TEXT NOT NULL, " +
                "`sourceApp` TEXT, `hcRecordId` TEXT)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_BodyReading_hcRecordId` ON `BodyReading` (`hcRecordId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_BodyReading_timestamp` ON `BodyReading` (`timestamp`)")
        db.execSQL(
            "INSERT INTO BodyReading (timestamp, metric, value, source, sourceApp, hcRecordId) " +
                "SELECT timestamp, 'WEIGHT_KG', weightKg, source, NULL, hcRecordId FROM BodyMeasurement",
        )
        db.execSQL(
            "INSERT INTO BodyReading (timestamp, metric, value, source, sourceApp, hcRecordId) " +
                "SELECT timestamp, 'BODY_FAT_PCT', bodyFatPct, source, NULL, NULL FROM BodyMeasurement WHERE bodyFatPct IS NOT NULL",
        )
        db.execSQL(
            "INSERT INTO BodyReading (timestamp, metric, value, source, sourceApp, hcRecordId) " +
                "SELECT timestamp, 'LEAN_MASS_KG', leanMassKg, source, NULL, NULL FROM BodyMeasurement WHERE leanMassKg IS NOT NULL",
        )
        db.execSQL("DROP TABLE BodyMeasurement")
    }
}
