package dev.matthewmorris.cuttracker.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matthewmorris.cuttracker.AppContainer
import dev.matthewmorris.cuttracker.CutTrackerApp
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.domain.Tags
import java.util.Locale

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as CutTrackerApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container) } })
}

/** A screen inside the bottom-tab shell; the shell owns the bottom insets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) },
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}

/** For list rows inside cards and sheets, which already have their own background. */
@Composable
fun transparentListItemColors(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

val defaultTags = listOf(Tags.ALCOHOL, Tags.TREAT)

@Composable
fun tagLabel(tag: String): String = when (tag) {
    Tags.ALCOHOL -> stringResource(R.string.tag_alcohol)
    Tags.TREAT -> stringResource(R.string.tag_treat)
    else -> tag.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/** Single-choice tag picker; tapping the selected tag clears it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(tags: List<String>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            FilterChip(
                selected = tag == selected,
                onClick = { onSelect(if (tag == selected) null else tag) },
                label = { Text(tagLabel(tag)) },
            )
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

fun Double.format(decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", this)

fun Int.formatThousands(): String = String.format(Locale.getDefault(), "%,d", this)
