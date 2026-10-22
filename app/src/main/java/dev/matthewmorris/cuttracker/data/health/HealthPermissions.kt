package dev.matthewmorris.cuttracker.data.health

import android.os.Build
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import dev.matthewmorris.cuttracker.BuildConfig
import dev.matthewmorris.cuttracker.domain.BodyMetric
import kotlin.reflect.KClass

object HealthPermissions {
    fun recordType(metric: BodyMetric): KClass<out Record> = when (metric) {
        BodyMetric.WEIGHT_KG -> WeightRecord::class
        BodyMetric.BODY_FAT_PCT -> BodyFatRecord::class
        BodyMetric.LEAN_MASS_KG -> LeanBodyMassRecord::class
    }

    fun read(metric: BodyMetric): String = HealthPermission.getReadPermission(recordType(metric))

    val readBody: Set<String> = BodyMetric.entries.map(::read).toSet()

    const val BACKGROUND = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    val writeNutrition: String = HealthPermission.getWritePermission(NutritionRecord::class)

    /**
     * Test weigh-ins are a debug tool for the emulator ("ranchu"). They are never offered on a real phone,
     * where they would land in real health data that other apps read.
     */
    val canWriteTestData: Boolean = BuildConfig.DEBUG && Build.HARDWARE == "ranchu"

    /** The matching write permissions are only declared in the debug manifest. */
    private val debugWrite: Set<String> =
        BodyMetric.entries.map { HealthPermission.getWritePermission(recordType(it)) }.toSet()

    /** Everything asked for in one dialog. Background read is only offered where the phone supports it. */
    fun toRequest(backgroundAvailable: Boolean): Set<String> = buildSet {
        addAll(readBody)
        add(writeNutrition)
        if (backgroundAvailable) add(BACKGROUND)
        if (canWriteTestData) addAll(debugWrite)
    }
}
