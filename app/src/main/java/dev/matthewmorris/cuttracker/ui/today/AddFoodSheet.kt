package dev.matthewmorris.cuttracker.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.data.db.FoodItem
import dev.matthewmorris.cuttracker.data.db.FoodLog
import dev.matthewmorris.cuttracker.ui.NumberField
import dev.matthewmorris.cuttracker.ui.TagPicker
import dev.matthewmorris.cuttracker.ui.format
import dev.matthewmorris.cuttracker.ui.formatThousands
import dev.matthewmorris.cuttracker.ui.toDecimalOrNull
import dev.matthewmorris.cuttracker.ui.transparentListItemColors

/**
 * ModalBottomSheet hands leftover fling velocity to a bouncy spring even when the sheet is already
 * fully open, so flicking up overshoots and bounces back. The sheet never needs upward velocity (it
 * always opens fully), so swallow it here. Downward flings still pass through to swipe-to-dismiss.
 */
private object NoUpwardFlingToSheet : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFoodSheet(
    state: AddSheetState,
    onDismiss: () -> Unit,
    onLogItem: (FoodItem) -> Unit,
    onRelog: (FoodLog) -> Unit,
    onQuickAdd: (name: String, kcal: Int, protein: Double, tag: String?, favourite: Boolean) -> Unit,
    onSaveItem: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
) {
    var editing by remember { mutableStateOf<FoodItem?>(null) }
    val newPreset = FoodItem(name = "", kcalPerServing = 0, proteinG = 0.0, isPreset = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // A drag on the handle would otherwise go straight to the sheet and skip the filter.
        dragHandle = {
            Box(Modifier.nestedScroll(NoUpwardFlingToSheet).verticalScroll(rememberScrollState())) {
                BottomSheetDefaults.DragHandle()
            }
        },
    ) {
        Column(
            Modifier
                .nestedScroll(NoUpwardFlingToSheet)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.treats_and_drinks), stringResource(R.string.preset_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.presets.forEach { preset ->
                    PresetChip(
                        title = preset.name,
                        subtitle = stringResource(R.string.kcal_value, preset.kcalPerServing.formatThousands()),
                        onClick = { onLogItem(preset) },
                        onLongClick = { editing = preset },
                    )
                }
                PresetChip(
                    title = stringResource(R.string.new_preset),
                    subtitle = null,
                    onClick = { editing = newPreset },
                    onLongClick = { editing = newPreset },
                )
            }

            if (state.favourites.isNotEmpty()) {
                SectionTitle(stringResource(R.string.favourites))
                state.favourites.forEach { item ->
                    FoodRow(item.name, item.kcalPerServing, item.proteinG, onClick = { onLogItem(item) })
                }
            }
            if (state.recents.isNotEmpty()) {
                SectionTitle(stringResource(R.string.recent))
                state.recents.forEach { log ->
                    FoodRow(log.name, log.kcal, log.proteinG, onClick = { onRelog(log) })
                }
            }

            SectionTitle(stringResource(R.string.quick_add))
            QuickAddForm(tags = state.tags, onAdd = onQuickAdd)
        }
    }

    editing?.let { item ->
        PresetDialog(
            item = item,
            tags = state.tags,
            onDismiss = { editing = null },
            onSave = { onSaveItem(it); editing = null },
            onDelete = if (item.id != 0L) ({ onDeleteItem(item); editing = null }) else null,
        )
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(title: String, subtitle: String?, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (subtitle == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FoodRow(name: String, kcal: Int, protein: Double, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        ListItem(
            headlineContent = { Text(name) },
            supportingContent = { Text(stringResource(R.string.protein_g, protein.format(0))) },
            trailingContent = { Text(stringResource(R.string.kcal_value, kcal.formatThousands())) },
            colors = transparentListItemColors(),
        )
    }
}

@Composable
private fun QuickAddForm(tags: List<String>, onAdd: (String, Int, Double, String?, Boolean) -> Unit) {
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }
    var favourite by remember { mutableStateOf(false) }
    val defaultName = stringResource(R.string.quick_add)
    val kcalValue = kcal.trim().toIntOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(kcal, { kcal = it }, stringResource(R.string.kcal_label), Modifier.weight(1f))
            NumberField(protein, { protein = it }, stringResource(R.string.protein_label), Modifier.weight(1f), decimal = true)
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.name_optional)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TagPicker(tags, tag, { tag = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = favourite, onCheckedChange = { favourite = it })
            Text(stringResource(R.string.save_as_favourite))
        }
        Button(
            onClick = {
                onAdd(name.trim().ifEmpty { defaultName }, kcalValue ?: 0, protein.toDecimalOrNull() ?: 0.0, tag, favourite)
            },
            enabled = kcalValue != null && kcalValue >= 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add))
        }
    }
}

@Composable
private fun PresetDialog(
    item: FoodItem,
    tags: List<String>,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(item.name) }
    var kcal by remember { mutableStateOf(if (item.id == 0L) "" else item.kcalPerServing.toString()) }
    var protein by remember { mutableStateOf(if (item.id == 0L) "" else item.proteinG.format(0)) }
    var tag by remember { mutableStateOf(item.tags.firstOrNull()) }
    val kcalValue = kcal.trim().toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (item.id == 0L) R.string.new_preset else R.string.edit_preset)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                NumberField(kcal, { kcal = it }, stringResource(R.string.kcal_label))
                NumberField(protein, { protein = it }, stringResource(R.string.protein_label), decimal = true)
                TagPicker(tags, tag, { tag = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        item.copy(
                            name = name.trim(),
                            kcalPerServing = kcalValue ?: 0,
                            proteinG = protein.toDecimalOrNull() ?: 0.0,
                            tags = setOfNotNull(tag),
                        ),
                    )
                },
                enabled = name.isNotBlank() && kcalValue != null,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
