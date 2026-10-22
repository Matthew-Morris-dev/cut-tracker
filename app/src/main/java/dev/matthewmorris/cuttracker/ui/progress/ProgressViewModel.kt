package dev.matthewmorris.cuttracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matthewmorris.cuttracker.AppContainer
import dev.matthewmorris.cuttracker.data.db.BodyReading
import dev.matthewmorris.cuttracker.data.health.HealthStatus
import dev.matthewmorris.cuttracker.domain.BodyMetric
import dev.matthewmorris.cuttracker.domain.DailyReading
import dev.matthewmorris.cuttracker.domain.MeasurementSource
import dev.matthewmorris.cuttracker.domain.Trends
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ChartRange(val days: Long?) { WEEK(7), MONTH(30), ALL(null) }

enum class ChartMetric(val metric: BodyMetric) { WEIGHT(BodyMetric.WEIGHT_KG), BODY_FAT(BodyMetric.BODY_FAT_PCT) }

data class ChartOptions(val range: ChartRange = ChartRange.MONTH, val metric: ChartMetric = ChartMetric.WEIGHT)

/** [from] and [to] are the range shown, which can be wider than the readings in it. */
data class ChartSeries(val points: List<DailyReading>, val average: List<DailyReading>, val from: LocalDate, val to: LocalDate)

data class ProgressState(
    val averageKg: Double? = null,
    val weeklyChangeKg: Double? = null,
    val goalWeightKg: Double? = null,
    val averageBodyFatPct: Double? = null,
    val weighIns: List<WeighIn> = emptyList(),
    val chartOptions: ChartOptions = ChartOptions(),
    val chart: ChartSeries? = null,
    val health: HealthStatus = HealthStatus(),
)

class ProgressViewModel(private val container: AppContainer) : ViewModel() {
    private val body = container.bodyRepository
    private val chartOptions = MutableStateFlow(ChartOptions())

    val state: StateFlow<ProgressState> = combine(
        body.readings,
        container.targetsRepository.current,
        container.today,
        container.healthSync.status,
        chartOptions,
    ) { readings, targets, today, health, options ->
        val weights = daily(readings, BodyMetric.WEIGHT_KG)
        val bodyFat = daily(readings, BodyMetric.BODY_FAT_PCT)
        ProgressState(
            averageKg = Trends.sevenDayAverage(weights, today),
            weeklyChangeKg = Trends.weeklyChange(weights, today),
            goalWeightKg = targets?.goalWeightKg,
            averageBodyFatPct = Trends.sevenDayAverage(bodyFat, today),
            weighIns = groupWeighIns(readings),
            chartOptions = options,
            chart = chart(daily(readings, options.metric.metric), options.range, today),
            health = health,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    fun setRange(range: ChartRange) = chartOptions.update { it.copy(range = range) }

    fun setMetric(metric: ChartMetric) = chartOptions.update { it.copy(metric = metric) }

    fun add(weightKg: Double, bodyFatPct: Double?) = viewModelScope.launch { body.addManual(weightKg, bodyFatPct) }

    /** Only hand-entered weigh-ins can be deleted here; Health Connect ones are deleted at their source. */
    fun delete(weighIn: WeighIn) = viewModelScope.launch {
        if (weighIn.source == MeasurementSource.MANUAL) body.delete(weighIn.readings)
    }

    fun sync() = viewModelScope.launch { container.healthSync.sync() }

    private fun daily(readings: List<BodyReading>, metric: BodyMetric): List<DailyReading> {
        val zone = container.clock.zone
        return readings.filter { it.metric == metric }.map { DailyReading(LocalDate.ofInstant(it.timestamp, zone), it.value) }
    }

    private fun chart(readings: List<DailyReading>, range: ChartRange, today: LocalDate): ChartSeries? {
        val means = Trends.dailyMeans(readings)
        val from = range.days?.let { today.minusDays(it - 1) } ?: means.firstOrNull()?.date ?: return null
        val points = means.filter { it.date in from..today }
        if (points.isEmpty()) return null
        return ChartSeries(points, Trends.movingAverage(readings, from, today), from, today)
    }
}
