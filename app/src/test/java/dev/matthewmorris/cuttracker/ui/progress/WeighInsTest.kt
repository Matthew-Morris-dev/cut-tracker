package dev.matthewmorris.cuttracker.ui.progress

import dev.matthewmorris.cuttracker.data.db.BodyReading
import dev.matthewmorris.cuttracker.domain.BodyMetric
import dev.matthewmorris.cuttracker.domain.MeasurementSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WeighInsTest {
    private val morning = Instant.parse("2026-10-22T06:30:00Z")
    private val renpho = "com.renpho.health"

    private fun reading(seconds: Long, metric: BodyMetric, value: Double, app: String? = renpho) = BodyReading(
        timestamp = morning.plusSeconds(seconds),
        metric = metric,
        value = value,
        source = if (app == null) MeasurementSource.MANUAL else MeasurementSource.HEALTH_CONNECT,
        sourceApp = app,
    )

    @Test fun `weight and body fat stamped seconds apart are one weigh-in`() {
        val weighIns = groupWeighIns(listOf(reading(0, BodyMetric.WEIGHT_KG, 84.2), reading(4, BodyMetric.BODY_FAT_PCT, 25.1)))
        assertEquals(1, weighIns.size)
        assertEquals(84.2, weighIns.single().value(BodyMetric.WEIGHT_KG)!!, 1e-9)
        assertEquals(25.1, weighIns.single().value(BodyMetric.BODY_FAT_PCT)!!, 1e-9)
    }

    @Test fun `readings more than five minutes apart are separate, newest first`() {
        val weighIns = groupWeighIns(listOf(reading(0, BodyMetric.WEIGHT_KG, 84.2), reading(3600, BodyMetric.WEIGHT_KG, 84.0)))
        assertEquals(listOf(84.0, 84.2), weighIns.map { it.value(BodyMetric.WEIGHT_KG) })
    }

    @Test fun `readings from different sources are never merged`() {
        val weighIns = groupWeighIns(listOf(reading(0, BodyMetric.WEIGHT_KG, 84.2), reading(10, BodyMetric.WEIGHT_KG, 84.3, app = null)))
        assertEquals(2, weighIns.size)
    }
}
