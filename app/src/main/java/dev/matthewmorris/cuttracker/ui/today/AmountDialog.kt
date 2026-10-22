package dev.matthewmorris.cuttracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.data.food.FoodChoice
import dev.matthewmorris.cuttracker.ui.NumberField
import dev.matthewmorris.cuttracker.ui.TagPicker
import dev.matthewmorris.cuttracker.ui.format
import dev.matthewmorris.cuttracker.ui.formatThousands
import dev.matthewmorris.cuttracker.ui.toDecimalOrNull
import kotlin.math.abs

/**
 * How much of a food kept per 100 g to log. Servings come first, as shortcuts; the gram field below
 * is the fix when Open Food Facts lists a wrong serving size, which it often does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmountDialog(
    choice: FoodChoice,
    tags: List<String>,
    onDismiss: () -> Unit,
    onLog: (grams: Double, tag: String?, favourite: Boolean) -> Unit,
) {
    var amount by remember(choice) { mutableStateOf(choice.defaultAmountG.formatAmount()) }
    var tag by remember(choice) { mutableStateOf(choice.tag) }
    var favourite by remember(choice) { mutableStateOf(choice.isFavourite) }
    val grams = amount.toDecimalOrNull()?.takeIf { it > 0 && it <= 10_000 }
    val unit = unitLabel(choice.isLiquid)
    val shortcuts = buildList {
        choice.servingG?.let { serving ->
            add(R.string.serving_half to serving / 2)
            add(R.string.serving_one to serving)
            add(R.string.serving_two to serving * 2)
        }
        choice.packG?.let { add(R.string.whole_pack to it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(choice.name)
                choice.brand?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    per100(choice.kcalPer100g, choice.proteinPer100g, choice.isLiquid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (shortcuts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shortcuts.forEach { (label, size) ->
                            FilterChip(
                                selected = grams != null && abs(grams - size) < 0.05,
                                onClick = { amount = size.formatAmount() },
                                label = { Text(stringResource(label, size.formatAmount(), unit)) },
                            )
                        }
                    }
                }
                NumberField(amount, { amount = it }, stringResource(R.string.amount_label, unit), decimal = true)
                Text(
                    grams?.let {
                        stringResource(R.string.amount_preview, choice.kcalFor(it).formatThousands(), choice.proteinFor(it).format(0))
                    } ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TagPicker(tags, tag, { tag = it })
                if (choice.itemId == null && choice.tag != null) {
                    Text(
                        stringResource(R.string.tag_suggested),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = favourite, onCheckedChange = { favourite = it })
                    Text(stringResource(R.string.save_as_favourite))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { grams?.let { onLog(it, tag, favourite) } }, enabled = grams != null) {
                Text(stringResource(R.string.log))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** 51.0 -> "51", 25.5 -> "25.5". */
private fun Double.formatAmount(): String = if (this % 1.0 == 0.0) toLong().toString() else format(1)
