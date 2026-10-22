package dev.matthewmorris.cuttracker.ui.progress

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.domain.BodyMetric
import dev.matthewmorris.cuttracker.domain.MeasurementSource
import dev.matthewmorris.cuttracker.ui.NumberField
import dev.matthewmorris.cuttracker.ui.ScreenScaffold
import dev.matthewmorris.cuttracker.ui.appViewModel
import dev.matthewmorris.cuttracker.ui.format
import dev.matthewmorris.cuttracker.ui.health.HealthConnectCard
import dev.matthewmorris.cuttracker.ui.health.rememberHealthConnectRequest
import dev.matthewmorris.cuttracker.ui.toDecimalOrNull
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProgressScreen() {
    val vm = appViewModel { ProgressViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val requestHealthConnect = rememberHealthConnectRequest(onResult = { vm.sync() })
    val timeFormat = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }

    ScreenScaffold(
        title = stringResource(R.string.tab_progress),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_weight)) },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HealthConnectCard(state.health, onRequest = requestHealthConnect, onSyncNow = { vm.sync() }) }
            item { TrendCard(state) }
            item { ChartCard(state, onRange = vm::setRange, onMetric = vm::setMetric) }
            item { TipsCard() }
            item { Text(stringResource(R.string.weigh_ins), style = MaterialTheme.typography.titleMedium) }
            if (state.weighIns.isEmpty()) {
                item { Text(stringResource(R.string.no_weigh_ins), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.weighIns, key = { "${it.timestamp}-${it.sourceApp}-${it.readings.first().id}" }) { weighIn ->
                WeighInRow(weighIn, timeFormat.format(weighIn.timestamp), onDelete = { vm.delete(weighIn) })
            }
        }
    }

    if (adding) {
        AddWeightDialog(onDismiss = { adding = false }, onSave = { kg, fat -> vm.add(kg, fat); adding = false })
    }
}

@Composable
private fun TrendCard(state: ProgressState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.seven_day_average), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                state.averageKg?.let { stringResource(R.string.kg_value, it.format(1)) } ?: stringResource(R.string.no_data),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            state.weeklyChangeKg?.let {
                val sign = if (it > 0) "+" else ""
                Text(stringResource(R.string.kg_per_week, sign + it.format(2)))
            }
            state.averageBodyFatPct?.let { Text(stringResource(R.string.average_body_fat, it.format(1))) }
            val average = state.averageKg
            val goal = state.goalWeightKg
            if (average != null && goal != null) {
                Text(
                    stringResource(R.string.to_goal, (average - goal).coerceAtLeast(0.0).format(1), goal.format(1)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChartCard(state: ProgressState, onRange: (ChartRange) -> Unit, onMetric: (ChartMetric) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val metrics = ChartMetric.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                metrics.forEachIndexed { index, metric ->
                    SegmentedButton(
                        selected = state.chartOptions.metric == metric,
                        onClick = { onMetric(metric) },
                        shape = SegmentedButtonDefaults.itemShape(index, metrics.size),
                    ) {
                        Text(stringResource(if (metric == ChartMetric.WEIGHT) R.string.chart_weight else R.string.chart_body_fat))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { range ->
                    val label = when (range) {
                        ChartRange.WEEK -> R.string.range_7_days
                        ChartRange.MONTH -> R.string.range_30_days
                        ChartRange.ALL -> R.string.range_all
                    }
                    FilterChip(
                        selected = state.chartOptions.range == range,
                        onClick = { onRange(range) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            val chart = state.chart
            if (chart == null) {
                Text(stringResource(R.string.chart_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TrendChart(chart)
                Text(
                    stringResource(R.string.chart_legend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TipsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.tips_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.tips_body), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WeighInRow(weighIn: WeighIn, time: String, onDelete: () -> Unit) {
    val weight = weighIn.value(BodyMetric.WEIGHT_KG)
    val bodyFat = weighIn.value(BodyMetric.BODY_FAT_PCT)
    val leanMass = weighIn.value(BodyMetric.LEAN_MASS_KG)
    val sourceLabel = when (weighIn.source) {
        MeasurementSource.MANUAL -> stringResource(R.string.source_manual)
        MeasurementSource.HEALTH_CONNECT -> appLabel(weighIn.sourceApp) ?: stringResource(R.string.health_connect)
    }
    ListItem(
        headlineContent = {
            Text(
                when {
                    weight != null -> stringResource(R.string.kg_value, weight.format(1))
                    bodyFat != null -> stringResource(R.string.body_fat_only, bodyFat.format(1))
                    else -> stringResource(R.string.lean_mass_value, (leanMass ?: 0.0).format(1))
                },
            )
        },
        supportingContent = { Text(stringResource(R.string.weigh_in_detail, time, sourceLabel)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (weight != null && bodyFat != null) Text(stringResource(R.string.body_fat_value, bodyFat.format(1)))
                if (weighIn.source == MeasurementSource.MANUAL) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        },
    )
}

/** The name of the app that wrote a reading, e.g. "Renpho Health". Null if it can't be looked up. */
@Composable
private fun appLabel(packageName: String?): String? {
    val packageManager = LocalContext.current.packageManager
    return remember(packageName) {
        packageName?.let {
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}

@Composable
private fun AddWeightDialog(onDismiss: () -> Unit, onSave: (Double, Double?) -> Unit) {
    var weight by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    val weightValue = weight.toDecimalOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_weight)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(weight, { weight = it }, stringResource(R.string.weight_kg), decimal = true)
                NumberField(fat, { fat = it }, stringResource(R.string.body_fat_optional), decimal = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(weightValue ?: 0.0, fat.toDecimalOrNull()) }, enabled = weightValue != null && weightValue > 0) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
