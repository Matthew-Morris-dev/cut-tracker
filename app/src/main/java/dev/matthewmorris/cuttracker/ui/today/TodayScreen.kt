package dev.matthewmorris.cuttracker.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.domain.GoalStatus
import dev.matthewmorris.cuttracker.ui.ScreenScaffold
import dev.matthewmorris.cuttracker.ui.appViewModel
import dev.matthewmorris.cuttracker.ui.format
import dev.matthewmorris.cuttracker.ui.formatThousands
import dev.matthewmorris.cuttracker.ui.tagLabel
import dev.matthewmorris.cuttracker.ui.theme.CalendarColours
import kotlin.math.abs

@Composable
fun TodayScreen() {
    val vm = appViewModel { TodayViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.sheetDone.collect { adding = false } }

    ScreenScaffold(
        title = stringResource(R.string.tab_today),
        floatingActionButton = {
            LargeFloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_food), modifier = Modifier.size(36.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TotalsCard(state) }
            if (state.goals.isNotEmpty()) item { GoalTicks(state.goals, onTap = vm::cycleGoal) }
            item {
                Text(
                    stringResource(R.string.todays_log),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.logs.isEmpty()) {
                item { Text(stringResource(R.string.nothing_logged), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.logs, key = { it.id }) { log ->
                ListItem(
                    headlineContent = { Text(log.name) },
                    supportingContent = {
                        val amount = log.grams?.let { stringResource(R.string.amount_value, it.format(0), stringResource(R.string.unit_g)) }
                        val tags = log.tags.map { tagLabel(it) }
                        val protein = stringResource(R.string.protein_g, log.proteinG.format(0))
                        Text((listOfNotNull(amount, protein) + tags).joinToString(" · "))
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.kcal_value, log.kcal.formatThousands()))
                            IconButton(onClick = { vm.deleteLog(log) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    },
                )
            }
        }
    }

    if (adding) {
        AddFoodSheet(vm, onDismiss = { adding = false })
    }
}

@Composable
private fun TotalsCard(state: TodayState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CalorieRing(eaten = state.kcalEaten, target = state.kcalTarget)
            Text(
                stringResource(R.string.kcal_eaten_of_target, state.kcalEaten.formatThousands(), state.kcalTarget.formatThousands()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    stringResource(R.string.protein_progress, state.proteinEaten.format(0), state.proteinTarget),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LinearProgressIndicator(
                    progress = { if (state.proteinTarget > 0) (state.proteinEaten / state.proteinTarget).toFloat().coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CalorieRing(eaten: Int, target: Int) {
    val over = target in 1..<eaten
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val fraction = if (target > 0) (eaten.toFloat() / target).coerceIn(0f, 1f) else 0f
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = stroke)
            if (fraction > 0f) drawArc(fill, startAngle = 135f, sweepAngle = 270f * fraction, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                abs(target - eaten).formatThousands(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(if (over) R.string.kcal_over else R.string.kcal_left),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalTicks(goals: List<GoalToday>, onTap: (GoalToday) -> Unit) {
    Column {
        Text(stringResource(R.string.todays_goals), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.goal_tap_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            goals.forEach { goal ->
                val (label, colour) = when (goal.status) {
                    GoalStatus.MET -> stringResource(R.string.status_met) to CalendarColours.green
                    GoalStatus.MISSED -> stringResource(R.string.status_missed) to CalendarColours.red
                    GoalStatus.UNMARKED -> stringResource(R.string.status_on_track) to MaterialTheme.colorScheme.onSurfaceVariant
                }
                val suffix = if (goal.manual) stringResource(R.string.status_set_by_you) else ""
                AssistChip(
                    onClick = { onTap(goal) },
                    label = { Text("${goal.goal.name}: $label$suffix") },
                    leadingIcon = {
                        when (goal.status) {
                            GoalStatus.MISSED -> Icon(Icons.Filled.Close, contentDescription = null, tint = colour)
                            else -> Icon(Icons.Filled.Check, contentDescription = null, tint = colour)
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}
