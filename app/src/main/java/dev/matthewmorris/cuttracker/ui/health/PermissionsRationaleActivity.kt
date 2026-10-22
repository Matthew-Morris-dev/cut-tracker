package dev.matthewmorris.cuttracker.ui.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.matthewmorris.cuttracker.R
import dev.matthewmorris.cuttracker.ui.theme.CutTrackerTheme

/**
 * What Health Connect shows when its privacy policy link is tapped. Health Connect won't ask for
 * permissions at all unless an app has this screen.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CutTrackerTheme {
                RationaleScreen(onClose = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RationaleScreen(onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rationale_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(R.string.rationale_reads_title, R.string.rationale_reads_body)
            Section(R.string.rationale_writes_title, R.string.rationale_writes_body)
            Section(R.string.rationale_where_title, R.string.rationale_where_body)
            Section(R.string.rationale_change_title, R.string.rationale_change_body)
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, @StringRes body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
    }
}
