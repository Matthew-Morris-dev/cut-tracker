package dev.matthewmorris.cuttracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matthewmorris.cuttracker.AppContainer
import dev.matthewmorris.cuttracker.data.db.BodyMeasurement
import dev.matthewmorris.cuttracker.domain.DailyReading
import dev.matthewmorris.cuttracker.domain.MeasurementSource
import dev.matthewmorris.cuttracker.domain.Trends
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class ProgressState(
    val averageKg: Double? = null,
    val weeklyChangeKg: Double? = null,
    val goalWeightKg: Double? = null,
    val averageBodyFatPct: Double? = null,
    val measurements: List<BodyMeasurement> = emptyList(),
)

class ProgressViewModel(private val container: AppContainer) : ViewModel() {
    private val body = container.bodyRepository

    val state: StateFlow<ProgressState> = combine(
        body.measurements,
        container.targetsRepository.current,
        container.today,
    ) { measurements, targets, today ->
        val zone = container.clock.zone
        fun dateOf(m: BodyMeasurement) = LocalDate.ofInstant(m.timestamp, zone)
        val weights = measurements.map { DailyReading(dateOf(it), it.weightKg) }
        val bodyFat = measurements.mapNotNull { m -> m.bodyFatPct?.let { DailyReading(dateOf(m), it) } }
        ProgressState(
            averageKg = Trends.sevenDayAverage(weights, today),
            weeklyChangeKg = Trends.weeklyChange(weights, today),
            goalWeightKg = targets?.goalWeightKg,
            averageBodyFatPct = Trends.sevenDayAverage(bodyFat, today),
            measurements = measurements,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    fun add(weightKg: Double, bodyFatPct: Double?) = viewModelScope.launch {
        body.add(
            BodyMeasurement(
                timestamp = Instant.now(container.clock),
                weightKg = weightKg,
                bodyFatPct = bodyFatPct,
                source = MeasurementSource.MANUAL,
            ),
        )
    }

    fun delete(measurement: BodyMeasurement) = viewModelScope.launch { body.delete(measurement) }
}
