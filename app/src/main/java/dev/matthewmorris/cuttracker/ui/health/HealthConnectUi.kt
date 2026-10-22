package dev.matthewmorris.cuttracker.ui.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.data.health.HealthAvailability
import dev.matthewmorris.cuttracker.data.health.HealthPermissions
import dev.matthewmorris.cuttracker.data.health.HealthStatus

/** Opens Health Connect's permission dialog; [onResult] runs when it closes, whatever was granted. */
@Composable
fun rememberHealthConnectRequest(onResult: () -> Unit): (Set<String>) -> Unit {
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        onResult()
    }
    return remember(launcher) { { permissions -> launcher.launch(permissions) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthConnectCard(
    status: HealthStatus,
    onRequest: (Set<String>) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.health_connect), style = MaterialTheme.typography.titleMedium)
            when {
                status.availability == HealthAvailability.UNAVAILABLE -> Body(stringResource(R.string.hc_unavailable))
                status.availability == HealthAvailability.UPDATE_REQUIRED -> {
                    Body(stringResource(R.string.hc_update_required))
                    Button(onClick = { openHealthConnectInStore(context) }) { Text(stringResource(R.string.hc_update)) }
                }
                !status.connected -> {
                    Body(stringResource(R.string.hc_not_connected))
                    Button(onClick = { onRequest(HealthPermissions.toRequest(status.backgroundAvailable)) }) {
                        Text(stringResource(R.string.hc_connect))
                    }
                }
                else -> {
                    Body(syncLine(status))
                    if (status.backgroundAvailable && !status.backgroundGranted) {
                        Body(stringResource(R.string.hc_background_off), muted = true)
                    }
                    if (!status.canWriteNutrition) Body(stringResource(R.string.hc_food_not_shared), muted = true)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.connected) {
                    OutlinedButton(onClick = onSyncNow, enabled = !status.syncing) { Text(stringResource(R.string.hc_sync_now)) }
                    if (status.backgroundAvailable && !status.backgroundGranted) {
                        OutlinedButton(onClick = { onRequest(setOf(HealthPermissions.BACKGROUND)) }) {
                            Text(stringResource(R.string.hc_turn_on_background))
                        }
                    }
                }
                extraActions()
            }
        }
    }
}

@Composable
private fun Body(text: String, muted: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun syncLine(status: HealthStatus): String {
    val last = status.lastSyncAt
    return when {
        status.syncing -> stringResource(R.string.hc_syncing)
        status.lastSyncFailed -> stringResource(R.string.hc_sync_failed)
        last == null -> stringResource(R.string.hc_connected)
        System.currentTimeMillis() - last.toEpochMilli() < DateUtils.MINUTE_IN_MILLIS -> stringResource(R.string.hc_last_synced_just_now)
        else -> stringResource(
            R.string.hc_last_synced,
            DateUtils.getRelativeTimeSpanString(last.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
        )
    }
}

private fun openHealthConnectInStore(context: Context) {
    val uri = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
    } catch (_: ActivityNotFoundException) {
        // No Play Store; nothing more the app can do.
    }
}
