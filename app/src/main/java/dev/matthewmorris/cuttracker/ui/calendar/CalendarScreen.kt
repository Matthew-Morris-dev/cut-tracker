package dev.matthewmorris.cuttracker.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.domain.GoalStatus
import dev.matthewmorris.cuttracker.ui.ScreenScaffold
import dev.matthewmorris.cuttracker.ui.appViewModel
import dev.matthewmorris.cuttracker.ui.formatThousands
import dev.matthewmorris.cuttracker.ui.tagLabel
import dev.matthewmorris.cuttracker.ui.transparentListItemColors
import dev.matthewmorris.cuttracker.ui.theme.CalendarColours
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen() {
    val vm = appViewModel { CalendarViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()

    ScreenScaffold(title = pluralStringResource(R.plurals.streak_title, state.streak.current, state.streak.current)) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoalFilter(state, vm::setFilter)
            Card {
                Column(Modifier.padding(12.dp)) {
                    MonthHeader(state, vm::showMonth)
                    MonthGrid(state, onSelect = vm::select)
                }
            }
            Counters(state)
        }
    }

    detail?.let { day ->
        DaySheet(day, onDismiss = { vm.select(null) }, onSet = { goalId, status -> vm.setManual(day.date, goalId, status) })
    }
}

@Composable
private fun GoalFilter(state: CalendarState, onFilter: (Long?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.filterGoalId == null,
            onClick = { onFilter(null) },
            label = { Text(stringResource(R.string.all_goals)) },
        )
        state.goals.forEach { goal ->
            FilterChip(selected = state.filterGoalId == goal.id, onClick = { onFilter(goal.id) }, label = { Text(goal.name) })
        }
    }
}

@Composable
private fun MonthHeader(state: CalendarState, onMonth: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonth(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
        }
        Text(
            state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onMonth(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarState, onSelect: (LocalDate) -> Unit) {
    val leadingBlanks = state.month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = List(leadingBlanks) { null } + state.days
    Row {
        DayOfWeek.entries.forEach { day ->
            Text(
                day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
    cells.chunked(7).forEach { week ->
        Row {
            (0 until 7).forEach { i ->
                val day = week.getOrNull(i)
                Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                    if (day != null) {
                        val fill = CalendarColours.of(day.colour)
                        var modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                        if (fill != null) modifier = modifier.background(fill, CircleShape)
                        if (day.isToday) modifier = modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        Box(modifier.clickable { onSelect(day.date) }, contentAlignment = Alignment.Center) {
                            Text(
                                day.date.dayOfMonth.toString(),
                                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (fill == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Counters(state: CalendarState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Counter(stringResource(R.string.current_streak), state.streak.current, Modifier.weight(1f))
        Counter(stringResource(R.string.best_streak), state.streak.best, Modifier.weight(1f))
        state.missedThisMonth?.let { missed ->
            val label = stringResource(if (state.filterGoalId == null) R.string.alcohol_days_this_month else R.string.missed_this_month)
            Counter(label, missed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value.formatThousands(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(day: DayDetail, onDismiss: () -> Unit, onSet: (Long, GoalStatus?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleLarge)
            if (day.goals.isEmpty()) Text(stringResource(R.string.no_goals_that_day))
            day.goals.forEach { goal ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(goal.goal.name, style = MaterialTheme.typography.titleMedium)
                    val options = listOf(
                        null to stringResource(R.string.override_auto),
                        GoalStatus.MET to stringResource(R.string.status_met),
                        GoalStatus.MISSED to stringResource(R.string.status_missed),
                    )
                    val current = if (goal.manual) goal.status else null
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (status, label) ->
                            SegmentedButton(
                                selected = current == status,
                                onClick = { onSet(goal.goal.id, status) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            ) { Text(label) }
                        }
                    }
                    if (!goal.manual) {
                        val autoLabel = when (goal.status) {
                            GoalStatus.MET -> R.string.auto_met
                            GoalStatus.MISSED -> R.string.auto_missed
                            GoalStatus.UNMARKED -> R.string.auto_unmarked
                        }
                        Text(stringResource(autoLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    day.logs.filter { goal.goal.avoidTag in it.tags }.forEach { log ->
                        Text(
                            stringResource(R.string.broken_by, log.name, tagLabel(goal.goal.avoidTag)),
                            color = CalendarColours.red,
                        )
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.logged_that_day), style = MaterialTheme.typography.titleMedium)
            if (day.logs.isEmpty()) Text(stringResource(R.string.nothing_logged), color = MaterialTheme.colorScheme.onSurfaceVariant)
            day.logs.forEach { log ->
                ListItem(
                    headlineContent = { Text(log.name) },
                    trailingContent = { Text(stringResource(R.string.kcal_value, log.kcal.formatThousands())) },
                    colors = transparentListItemColors(),
                )
            }
        }
    }
}
