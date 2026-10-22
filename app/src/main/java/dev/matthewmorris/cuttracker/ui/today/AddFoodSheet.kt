package dev.matthewmorris.cuttracker.ui.today

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.data.db.FoodItem
import dev.matthewmorris.cuttracker.data.food.FoodProduct
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
fun AddFoodSheet(vm: TodayViewModel, onDismiss: () -> Unit) {
    val state by vm.sheet.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val choice by vm.choice.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FoodItem?>(null) }
    val newPreset = FoodItem(name = "", kcalPerServing = 0, proteinG = 0.0, isPreset = true)
    val startScan = rememberBarcodeScanner(
        onBarcode = vm::onBarcode,
        onPreparing = vm::onScannerPreparing,
        onReady = vm::onScannerReady,
        onFailed = vm::onScannerFailed,
    )
    DisposableEffect(vm) { onDispose { vm.resetSheet() } }

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
            SearchRow(query = search.query, onQueryChange = vm::setQuery, onSearch = vm::searchOnline, onScan = startScan)
            ScanStatus(scan)
            val notFound = scan as? ScanState.NotFound
            when {
                notFound != null -> QuickAddForm(
                    tags = state.tags,
                    onAdd = { name, kcal, protein, tag, favourite ->
                        vm.quickAdd(name, kcal, protein, tag, favourite, barcode = notFound.barcode)
                        onDismiss()
                    },
                )
                search.query.isNotBlank() -> SearchResults(
                    search = search,
                    onPickSaved = vm::pick,
                    onPickProduct = vm::choose,
                    onSearchOnline = vm::searchOnline,
                )
                else -> {
                    SectionTitle(stringResource(R.string.treats_and_drinks), stringResource(R.string.preset_hint))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.presets.forEach { preset ->
                            PresetChip(
                                title = preset.name,
                                subtitle = stringResource(R.string.kcal_value, preset.kcalPerServing.formatThousands()),
                                onClick = { vm.logItem(preset); onDismiss() },
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
                            FoodRow(item.name, savedSubtitle(item), kcalText(item), onClick = { vm.pick(item) })
                        }
                    }
                    if (state.recents.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.recent))
                        state.recents.forEach { log ->
                            FoodRow(
                                title = log.name,
                                subtitle = stringResource(R.string.protein_g, log.proteinG.format(0)),
                                trailing = stringResource(R.string.kcal_value, log.kcal.formatThousands()),
                                onClick = { vm.relog(log); onDismiss() },
                            )
                        }
                    }

                    SectionTitle(stringResource(R.string.quick_add))
                    QuickAddForm(
                        tags = state.tags,
                        onAdd = { name, kcal, protein, tag, favourite ->
                            vm.quickAdd(name, kcal, protein, tag, favourite)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }

    choice?.let { food ->
        AmountDialog(
            choice = food,
            tags = state.tags,
            onDismiss = vm::dismissChoice,
            onLog = { grams, tag, favourite -> vm.logChoice(food, grams, tag, favourite) },
        )
    }
    editing?.let { item ->
        PresetDialog(
            item = item,
            tags = state.tags,
            onDismiss = { editing = null },
            onSave = { vm.saveItem(it); editing = null },
            onDelete = if (item.id != 0L) ({ vm.deleteItem(item); editing = null }) else null,
        )
    }
}

@Composable
private fun SearchRow(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit, onScan: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_foods)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearch()
                keyboard?.hide()
            }),
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = onScan, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = stringResource(R.string.scan_barcode))
        }
    }
}

@Composable
private fun ScanStatus(scan: ScanState) {
    when (scan) {
        ScanState.Idle -> Unit
        ScanState.PreparingScanner -> Progress(stringResource(R.string.preparing_scanner))
        ScanState.LookingUp -> Progress(stringResource(R.string.looking_up_barcode))
        is ScanState.NotFound -> Text(stringResource(R.string.barcode_not_found, scan.barcode))
        is ScanState.Failed -> Text(
            stringResource(scan.problem?.let(::problemMessage) ?: R.string.scanner_failed),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Progress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text)
    }
}

@Composable
private fun SearchResults(
    search: FoodSearchState,
    onPickSaved: (FoodItem) -> Unit,
    onPickProduct: (FoodProduct) -> Unit,
    onSearchOnline: () -> Unit,
) {
    if (search.saved.isNotEmpty()) {
        SectionTitle(stringResource(R.string.your_foods))
        search.saved.forEach { item -> FoodRow(item.name, savedSubtitle(item), kcalText(item), onClick = { onPickSaved(item) }) }
    }
    SectionTitle(stringResource(R.string.open_food_facts))
    when (val online = search.online) {
        OnlineSearch.NotRun -> OutlinedButton(onClick = onSearchOnline, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.search_off, search.query.trim()))
        }
        OnlineSearch.Searching -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is OnlineSearch.Failed -> Text(stringResource(problemMessage(online.problem)), color = MaterialTheme.colorScheme.error)
        is OnlineSearch.Done -> if (online.results.isEmpty()) {
            Text(stringResource(R.string.nothing_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            online.results.forEach { product ->
                FoodRow(
                    title = product.name,
                    subtitle = listOfNotNull(
                        product.brand,
                        per100(product.kcalPer100g, product.proteinPer100g, product.isLiquid),
                    ).joinToString(" · "),
                    trailing = null,
                    onClick = { onPickProduct(product) },
                )
            }
        }
    }
    Text(
        stringResource(R.string.off_credit),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@StringRes
private fun problemMessage(problem: LookupProblem): Int = when (problem) {
    LookupProblem.NO_CONNECTION -> R.string.lookup_no_connection
    LookupProblem.TOO_MANY_REQUESTS -> R.string.lookup_too_many
}

@Composable
private fun savedSubtitle(item: FoodItem): String {
    val kcal100 = item.kcalPer100g ?: return stringResource(R.string.protein_g, item.proteinG.format(0))
    return listOfNotNull(item.brand, per100(kcal100, item.proteinPer100g ?: 0.0, item.isLiquid)).joinToString(" · ")
}

/** Per-serving foods show their calories; foods kept per 100 g show them in the subtitle instead. */
@Composable
private fun kcalText(item: FoodItem): String? =
    if (item.kcalPer100g == null) stringResource(R.string.kcal_value, item.kcalPerServing.formatThousands()) else null

@Composable
fun per100(kcal: Double, protein: Double, liquid: Boolean): String =
    stringResource(R.string.per_100, kcal.format(0), protein.format(1), unitLabel(liquid))

@Composable
fun unitLabel(liquid: Boolean): String = stringResource(if (liquid) R.string.unit_ml else R.string.unit_g)

/**
 * Google's scanner from Play services: its own camera screen, so the app needs no camera permission.
 * Play services downloads the scanner on demand, so the first scan may wait for that download.
 */
@Composable
private fun rememberBarcodeScanner(
    onBarcode: (String) -> Unit,
    onPreparing: () -> Unit,
    onReady: () -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnPreparing by rememberUpdatedState(onPreparing)
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnFailed by rememberUpdatedState(onFailed)
    val moduleInstall = remember(context) { ModuleInstall.getClient(context) }
    val scanner = remember(context) {
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                // Food packs carry retail barcodes; ignoring QR codes and the rest avoids stray reads.
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .enableAutoZoom()
                .build(),
        )
    }
    return remember(scanner, moduleInstall) {
        val scan = {
            scanner.startScan()
                .addOnSuccessListener { barcode -> barcode.rawValue?.let(currentOnBarcode) }
                .addOnFailureListener { currentOnFailed() }
        }
        {
            moduleInstall.areModulesAvailable(scanner)
                .addOnSuccessListener { availability ->
                    if (availability.areModulesAvailable()) {
                        scan()
                    } else {
                        currentOnPreparing()
                        moduleInstall.installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
                            .addOnSuccessListener {
                                currentOnReady()
                                scan()
                            }
                            .addOnFailureListener { currentOnFailed() }
                    }
                }
                .addOnFailureListener { currentOnFailed() }
        }
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
private fun FoodRow(title: String, subtitle: String, trailing: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            trailingContent = trailing?.let { { Text(it) } },
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
