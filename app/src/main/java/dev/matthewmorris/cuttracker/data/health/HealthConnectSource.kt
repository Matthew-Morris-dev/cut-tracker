package dev.matthewmorris.cuttracker.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import dev.matthewmorris.cuttracker.domain.BodyMetric
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class HealthConnectSource(private val context: Context) : HealthDataSource {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        else -> HealthAvailability.UNAVAILABLE
    }

    override suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    override suspend fun backgroundReadAvailable(): Boolean =
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    override suspend fun read(metric: BodyMetric, from: Instant, to: Instant): List<HealthReading> {
        val readings = mutableListOf<HealthReading>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HealthPermissions.recordType(metric),
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = pageToken,
                ),
            )
            response.records.mapNotNullTo(readings) { it.toReading() }
            // Android 12 and 13 return "" rather than null after the last page.
            pageToken = response.pageToken?.takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return readings
    }

    override suspend fun changesToken(metric: BodyMetric): String =
        client.getChangesToken(ChangesTokenRequest(setOf(HealthPermissions.recordType(metric))))

    override suspend fun changes(token: String): ChangesPage {
        val response = client.getChanges(token)
        return ChangesPage(
            changes = response.changes.mapNotNull { change ->
                when (change) {
                    is UpsertionChange -> change.record.toReading()?.let(HealthChange::Upsert)
                    is DeletionChange -> HealthChange.Delete(change.recordId)
                    else -> null
                }
            },
            nextToken = response.nextChangesToken,
            hasMore = response.hasMore,
            tokenExpired = response.changesTokenExpired,
        )
    }

    override suspend fun upsertNutrition(entries: List<NutritionEntry>) {
        if (entries.isEmpty()) return
        client.insertRecords(entries.map { it.toRecord() })
    }

    override suspend fun deleteNutrition(clientIds: List<String>) {
        if (clientIds.isEmpty()) return
        client.deleteRecords(NutritionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = clientIds)
    }

    /** Emulator debug builds only (see [HealthPermissions.canWriteTestData]): two weeks of morning weigh-ins. */
    suspend fun writeTestWeighIns(clock: Clock) {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val records = (13 downTo 0).flatMap { daysAgo ->
            val time = today.minusDays(daysAgo.toLong()).atTime(LocalTime.of(7, 30)).atZone(zone).toInstant()
            val offset = offsetAt(time)
            val step = 13 - daysAgo
            val wobble = if (step % 3 == 0) 0.3 else if (step % 3 == 1) -0.2 else 0.0
            listOf<Record>(
                WeightRecord(time, offset, Mass.kilograms(84.6 - step * 0.07 + wobble), Metadata.manualEntry()),
                BodyFatRecord(time, offset, Percentage(25.0 - step * 0.03), Metadata.manualEntry()),
            )
        }
        client.insertRecords(records)
    }

    private fun Record.toReading(): HealthReading? {
        val source = metadata.dataOrigin.packageName
        return when (this) {
            is WeightRecord -> HealthReading(metadata.id, BodyMetric.WEIGHT_KG, time, weight.inKilograms, source)
            is BodyFatRecord -> HealthReading(metadata.id, BodyMetric.BODY_FAT_PCT, time, percentage.value, source)
            is LeanBodyMassRecord -> HealthReading(metadata.id, BodyMetric.LEAN_MASS_KG, time, mass.inKilograms, source)
            else -> null
        }
    }

    private fun NutritionEntry.toRecord() = NutritionRecord(
        startTime = start,
        startZoneOffset = offsetAt(start),
        endTime = end,
        endZoneOffset = offsetAt(end),
        energy = Energy.kilocalories(kcal),
        protein = Mass.grams(proteinG),
        name = name,
        metadata = Metadata.manualEntry(clientRecordId = clientId),
    )

    private fun offsetAt(time: Instant): ZoneOffset = ZoneId.systemDefault().rules.getOffset(time)
}
