package dev.matthewmorris.cuttracker.data.health

import dev.matthewmorris.cuttracker.domain.BodyMetric
import java.time.Instant

/** Health Connect in memory. Tokens are "METRIC:position in the change log". */
class FakeHealthDataSource : HealthDataSource {
    var availability = HealthAvailability.AVAILABLE
    var granted: Set<String> = HealthPermissions.readBody + HealthPermissions.writeNutrition + HealthPermissions.BACKGROUND
    var backgroundAvailable = true
    var tokensExpired = false
    var failNutrition = false
    var pageSize = Int.MAX_VALUE
    var reads = 0
    var nutritionUpserts = 0

    private val records = linkedMapOf<String, HealthReading>()
    private val changeLog = mutableListOf<Pair<BodyMetric, HealthChange>>()
    val nutrition = linkedMapOf<String, NutritionEntry>()

    fun put(reading: HealthReading) {
        records[reading.id] = reading
        changeLog += reading.metric to HealthChange.Upsert(reading)
    }

    fun remove(id: String) {
        val reading = records.remove(id) ?: return
        changeLog += reading.metric to HealthChange.Delete(id)
    }

    /** Gone without a change the app will ever see, as happens while a token is expired. */
    fun removeSilently(id: String) {
        records.remove(id)
    }

    override fun availability() = availability

    override suspend fun grantedPermissions() = granted

    override suspend fun backgroundReadAvailable() = backgroundAvailable

    override suspend fun read(metric: BodyMetric, from: Instant, to: Instant): List<HealthReading> {
        reads++
        return records.values.filter { it.metric == metric && it.time in from..to }
    }

    override suspend fun changesToken(metric: BodyMetric) = "${metric.name}:${changeLog.size}"

    override suspend fun changes(token: String): ChangesPage {
        if (tokensExpired) return ChangesPage(emptyList(), token, hasMore = false, tokenExpired = true)
        val (name, position) = token.split(":")
        val metric = BodyMetric.valueOf(name)
        val pending = changeLog.withIndex().drop(position.toInt()).filter { it.value.first == metric }
        val page = pending.take(pageSize)
        val hasMore = pending.size > page.size
        val next = if (hasMore) page.last().index + 1 else changeLog.size
        return ChangesPage(page.map { it.value.second }, "$name:$next", hasMore, tokenExpired = false)
    }

    override suspend fun upsertNutrition(entries: List<NutritionEntry>) {
        check(!failNutrition) { "Health Connect unavailable" }
        nutritionUpserts++
        entries.forEach { nutrition[it.clientId] = it }
    }

    override suspend fun deleteNutrition(clientIds: List<String>) {
        check(!failNutrition) { "Health Connect unavailable" }
        clientIds.forEach { nutrition.remove(it) }
    }
}
