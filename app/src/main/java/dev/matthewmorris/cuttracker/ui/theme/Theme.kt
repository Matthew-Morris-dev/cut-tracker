package dev.matthewmorris.cuttracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.matthewmorris.cuttracker.domain.DayColour

private val colours = darkColorScheme(
    primary = Color(0xFF7BD88F),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF1E5130),
    onPrimaryContainer = Color(0xFFB6F2C1),
    secondary = Color(0xFFB4CCB8),
    onSecondary = Color(0xFF203527),
    secondaryContainer = Color(0xFF2F4A37),
    onSecondaryContainer = Color(0xFFD0E8D3),
    tertiary = Color(0xFFFFB86B),
    error = Color(0xFFFF8A80),
    background = Color(0xFF111412),
    surface = Color(0xFF111412),
    surfaceVariant = Color(0xFF2C322D),
    onSurfaceVariant = Color(0xFFBFC9C0),
    surfaceContainerLowest = Color(0xFF0C0F0D),
    surfaceContainerLow = Color(0xFF191D1A),
    surfaceContainer = Color(0xFF1D211E),
    surfaceContainerHigh = Color(0xFF272C28),
    surfaceContainerHighest = Color(0xFF222723),
    outline = Color(0xFF89938A),
    outlineVariant = Color(0xFF3F4840),
)

object CalendarColours {
    val green = Color(0xFF3FA65A)
    val amber = Color(0xFFE5A630)
    val red = Color(0xFFD9483B)
    val grey = Color(0xFF3A3F3B)

    fun of(colour: DayColour): Color? = when (colour) {
        DayColour.GREEN -> green
        DayColour.AMBER -> amber
        DayColour.RED -> red
        DayColour.GREY -> grey
        DayColour.NONE -> null
    }
}

/** Dark by default, as the spec asks. */
@Composable
fun CutTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colours, content = content)
}
