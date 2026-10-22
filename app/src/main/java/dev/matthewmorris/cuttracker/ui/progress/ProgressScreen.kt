package dev.matthewmorris.cuttracker.ui.progress

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.ui.NumberField
import dev.matthewmorris.cuttracker.ui.ScreenScaffold
import dev.matthewmorris.cuttracker.ui.appViewModel
import dev.matthewmorris.cuttracker.ui.format
import dev.matthewmorris.cuttracker.ui.toDecimalOrNull
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProgressScreen() {
    val vm = appViewModel { ProgressViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
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
            item { TrendCard(state) }
            item { TipsCard() }
            item { Text(stringResource(R.string.weigh_ins), style = MaterialTheme.typography.titleMedium) }
            if (state.measurements.isEmpty()) {
                item { Text(stringResource(R.string.no_weigh_ins), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.measurements, key = { it.id }) { m ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.kg_value, m.weightKg.format(1))) },
                    supportingContent = { Text(timeFormat.format(m.timestamp)) },
                    trailingContent = {
                        Row {
                            m.bodyFatPct?.let { Text(stringResource(R.string.body_fat_value, it.format(1)), modifier = Modifier.padding(top = 12.dp)) }
                            IconButton(onClick = { vm.delete(m) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    },
                )
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
private fun TipsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.tips_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.tips_body), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.health_connect_coming),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
