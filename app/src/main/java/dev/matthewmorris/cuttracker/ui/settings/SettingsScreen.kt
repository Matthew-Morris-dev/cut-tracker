package dev.matthewmorris.cuttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.data.db.Goal
import dev.matthewmorris.cuttracker.data.db.Targets
import dev.matthewmorris.cuttracker.domain.Phase
import dev.matthewmorris.cuttracker.ui.NumberField
import dev.matthewmorris.cuttracker.ui.ScreenScaffold
import dev.matthewmorris.cuttracker.ui.TagPicker
import dev.matthewmorris.cuttracker.ui.appViewModel
import dev.matthewmorris.cuttracker.ui.defaultTags
import dev.matthewmorris.cuttracker.ui.format
import dev.matthewmorris.cuttracker.ui.tagLabel
import dev.matthewmorris.cuttracker.ui.toDecimalOrNull
import dev.matthewmorris.cuttracker.ui.transparentListItemColors
import java.util.Locale

@Composable
fun SettingsScreen() {
    val vm = appViewModel { SettingsViewModel(it) }
    val targets by vm.targets.collectAsStateWithLifecycle()
    val goals by vm.allGoals.collectAsStateWithLifecycle()
    var editingGoal by remember { mutableStateOf<Goal?>(null) }
    var addingGoal by remember { mutableStateOf(false) }

    ScreenScaffold(title = stringResource(R.string.tab_settings)) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            targets?.let { current -> TargetsCard(current, vm::saveTargets) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.daily_goals), style = MaterialTheme.typography.titleMedium)
                    goals.forEach { goal ->
                        Surface(onClick = { editingGoal = goal }, color = Color.Transparent) {
                            ListItem(
                                headlineContent = { Text(goal.name) },
                                supportingContent = {
                                    val parts = buildList {
                                        add(stringResource(R.string.goal_avoids, tagLabel(goal.avoidTag)))
                                        goal.weeklyAllowance?.let { add(stringResource(R.string.goal_allowance, it)) }
                                        if (goal.archived) add(stringResource(R.string.archived))
                                    }
                                    Text(parts.joinToString(" · "))
                                },
                                colors = transparentListItemColors(),
                            )
                        }
                    }
                    OutlinedButton(onClick = { addingGoal = true }) { Text(stringResource(R.string.add_goal)) }
                }
            }
        }
    }

    if (addingGoal) {
        GoalDialog(
            goal = null,
            tags = (defaultTags + goals.map { it.avoidTag }).distinct(),
            onDismiss = { addingGoal = false },
            onSave = { name, tag, allowance, _ -> vm.addGoal(name, tag, allowance); addingGoal = false },
        )
    }
    editingGoal?.let { goal ->
        GoalDialog(
            goal = goal,
            tags = (defaultTags + goals.map { it.avoidTag }).distinct(),
            onDismiss = { editingGoal = null },
            onSave = { name, tag, allowance, archived ->
                vm.updateGoal(goal.copy(name = name, avoidTag = tag, weeklyAllowance = allowance, archived = archived))
                editingGoal = null
            },
        )
    }
}

@Composable
private fun TargetsCard(current: Targets, onSave: (Targets) -> Unit) {
    var kcal by remember(current) { mutableStateOf(current.kcalTarget.toString()) }
    var protein by remember(current) { mutableStateOf(current.proteinTarget.toString()) }
    var goalWeight by remember(current) { mutableStateOf(current.goalWeightKg.format(1)) }
    var rate by remember(current) { mutableStateOf(current.targetRateKgPerWeek.format(2)) }
    var phase by remember(current) { mutableStateOf(current.phase) }
    val updated = current.copy(
        kcalTarget = kcal.trim().toIntOrNull() ?: -1,
        proteinTarget = protein.trim().toIntOrNull() ?: -1,
        goalWeightKg = goalWeight.toDecimalOrNull() ?: -1.0,
        targetRateKgPerWeek = rate.toDecimalOrNull() ?: -1.0,
        phase = phase,
    )
    val valid = updated.kcalTarget > 0 && updated.proteinTarget >= 0 && updated.goalWeightKg > 0 && updated.targetRateKgPerWeek >= 0

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.targets), style = MaterialTheme.typography.titleMedium)
            val phases = Phase.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                phases.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = phase == p,
                        onClick = { phase = p },
                        shape = SegmentedButtonDefaults.itemShape(index, phases.size),
                    ) { Text(stringResource(if (p == Phase.CUT) R.string.phase_cut else R.string.phase_build)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(kcal, { kcal = it }, stringResource(R.string.kcal_per_day), Modifier.weight(1f))
                NumberField(protein, { protein = it }, stringResource(R.string.protein_per_day), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(goalWeight, { goalWeight = it }, stringResource(R.string.goal_weight_kg), Modifier.weight(1f), decimal = true)
                NumberField(rate, { rate = it }, stringResource(R.string.rate_kg_per_week), Modifier.weight(1f), decimal = true)
            }
            Button(onClick = { onSave(updated) }, enabled = valid && updated != current, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_targets))
            }
        }
    }
}

@Composable
private fun GoalDialog(
    goal: Goal?,
    tags: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, tag: String, allowance: Int?, archived: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(goal?.name.orEmpty()) }
    var tag by remember { mutableStateOf(goal?.avoidTag) }
    var newTag by remember { mutableStateOf("") }
    var allowance by remember { mutableStateOf(goal?.weeklyAllowance?.toString().orEmpty()) }
    var archived by remember { mutableStateOf(goal?.archived ?: false) }
    val chosenTag = newTag.trim().lowercase(Locale.ROOT).ifEmpty { null } ?: tag
    val allowanceValue = allowance.trim().ifEmpty { null }?.toIntOrNull()
    val allowanceValid = allowance.isBlank() || (allowanceValue != null && allowanceValue in 0..7)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (goal == null) R.string.add_goal else R.string.edit_goal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                Text(stringResource(R.string.goal_tag_hint), style = MaterialTheme.typography.bodySmall)
                TagPicker(tags, tag, { tag = it; newTag = "" })
                OutlinedTextField(
                    newTag,
                    { newTag = it.replace(",", "") },
                    label = { Text(stringResource(R.string.or_new_tag)) },
                    singleLine = true,
                )
                NumberField(allowance, { allowance = it }, stringResource(R.string.weekly_allowance))
                if (goal != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.archived), modifier = Modifier.weight(1f))
                        Switch(checked = archived, onCheckedChange = { archived = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), chosenTag!!, allowanceValue?.takeIf { it > 0 }, archived) },
                enabled = name.isNotBlank() && chosenTag != null && allowanceValid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
