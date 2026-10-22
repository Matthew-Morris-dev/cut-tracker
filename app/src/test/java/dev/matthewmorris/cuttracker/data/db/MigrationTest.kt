package dev.matthewmorris.cuttracker.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun `v2 splits each measurement into one reading per metric`() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO BodyMeasurement (timestamp, weightKg, bodyFatPct, leanMassKg, source, hcRecordId) " +
                    "VALUES (1000, 84.2, 25.0, 63.1, 'MANUAL', NULL), (2000, 83.9, NULL, NULL, 'MANUAL', NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        val rows = db.query("SELECT timestamp, metric, value, source FROM BodyReading ORDER BY timestamp, metric").use { c ->
            buildList {
                while (c.moveToNext()) add(listOf(c.getLong(0), c.getString(1), c.getDouble(2), c.getString(3)))
            }
        }
        assertEquals(
            listOf(
                listOf(1000L, "BODY_FAT_PCT", 25.0, "MANUAL"),
                listOf(1000L, "LEAN_MASS_KG", 63.1, "MANUAL"),
                listOf(1000L, "WEIGHT_KG", 84.2, "MANUAL"),
                listOf(2000L, "WEIGHT_KG", 83.9, "MANUAL"),
            ),
            rows,
        )
        val oldTable = db.query("SELECT name FROM sqlite_master WHERE name = 'BodyMeasurement'").use { it.count }
        assertEquals(0, oldTable)
    }

    private companion object {
        const val DB = "migration-test"
    }
}
