package dev.matthewmorris.cuttracker.ui.progress

import dev.matthewmorris.cuttracker.data.db.BodyReading
import dev.matthewmorris.cuttracker.domain.BodyMetric
import dev.matthewmorris.cuttracker.domain.MeasurementSource
import java.time.Duration
import java.time.Instant

/** What the scale shows as one weigh-in: the readings one app recorded at about the same moment. */
data class WeighIn(
    val timestamp: Instant,
    val source: MeasurementSource,
    val sourceApp: String?,
    val readings: List<BodyReading>,
) {
    fun value(metric: BodyMetric): Double? = readings.firstOrNull { it.metric == metric }?.value
}

/**
 * Groups readings into weigh-ins, newest first. Health Connect keeps weight and body fat as separate
 * records, and an app may stamp them a few seconds apart, so readings from the same source within
 * [window] of the group's newest reading are one weigh-in.
 */
fun groupWeighIns(readings: List<BodyReading>, window: Duration = Duration.ofMinutes(5)): List<WeighIn> {
    val groups = mutableListOf<MutableList<BodyReading>>()
    for (reading in readings.sortedByDescending { it.timestamp }) {
        val group = groups.lastOrNull()
        val newest = group?.first()
        if (group != null && newest != null &&
            newest.source == reading.source &&
            newest.sourceApp == reading.sourceApp &&
            Duration.between(reading.timestamp, newest.timestamp) <= window
        ) {
            group.add(reading)
        } else {
            groups.add(mutableListOf(reading))
        }
    }
    return groups.map { WeighIn(it.first().timestamp, it.first().source, it.first().sourceApp, it) }
}
