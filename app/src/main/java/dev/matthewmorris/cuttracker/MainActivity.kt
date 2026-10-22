package dev.matthewmorris.cuttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.matthewmorris.cuttracker.ui.CutTrackerRoot
import dev.matthewmorris.cuttracker.ui.theme.CutTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CutTrackerTheme {
                CutTrackerRoot()
            }
        }
    }
}
