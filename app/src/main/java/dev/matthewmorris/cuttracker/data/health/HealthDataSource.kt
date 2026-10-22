package dev.matthewmorris.cuttracker.data.health

import dev.matthewmorris.cuttracker.domain.BodyMetric
import java.time.Instant

enum class HealthAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

/** One Health Connect record (a weight, body fat or lean mass), in the units the app stores. */
data class HealthReading(
    val id: String,
    val metric: BodyMetric,
    val time: Instant,
    val value: Double,
    val sourceApp: String,
)

sealed interface HealthChange {
    data class Upsert(val reading: HealthReading) : HealthChange

    /** Health Connect only reports the record id of a deletion. */
    data class Delete(val id: String) : HealthChange
}

data class ChangesPage(
    val changes: List<HealthChange>,
    val nextToken: String,
    val hasMore: Boolean,
    val tokenExpired: Boolean,
)

/** A logged food as Health Connect sees it. [clientId] lets the app overwrite or delete it later. */
data class NutritionEntry(
    val clientId: String,
    val start: Instant,
    val end: Instant,
    val name: String,
    val kcal: Double,
    val proteinG: Double,
)

/** The parts of Health Connect the app uses, so sync can be tested without it. */
interface HealthDataSource {
    fun availability(): HealthAvailability

    suspend fun grantedPermissions(): Set<String>

    suspend fun backgroundReadAvailable(): Boolean

    suspend fun read(metric: BodyMetric, from: Instant, to: Instant): List<HealthReading>

    /** One token per metric, as Health Connect recommends, so a revoked permission only stops that metric. */
    suspend fun changesToken(metric: BodyMetric): String

    suspend fun changes(token: String): ChangesPage

    suspend fun upsertNutrition(entries: List<NutritionEntry>)

    suspend fun deleteNutrition(clientIds: List<String>)
}
