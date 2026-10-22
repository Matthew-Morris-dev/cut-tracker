package dev.matthewmorris.cuttracker.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.ui.calendar.CalendarScreen
import dev.matthewmorris.cuttracker.ui.progress.ProgressScreen
import dev.matthewmorris.cuttracker.ui.settings.SettingsScreen
import dev.matthewmorris.cuttracker.ui.today.TodayScreen

private enum class Tab(@param:StringRes val label: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Outlined.Today),
    CALENDAR(R.string.tab_calendar, Icons.Outlined.CalendarMonth),
    PROGRESS(R.string.tab_progress, Icons.AutoMirrored.Outlined.ShowChart),
    SETTINGS(R.string.tab_settings, Icons.Outlined.Settings),
}

@Composable
fun CutTrackerRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.TODAY -> TodayScreen()
                Tab.CALENDAR -> CalendarScreen()
                Tab.PROGRESS -> ProgressScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
