package com.example.myapplication.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.entities.RecognitionLogEntity
import com.example.myapplication.data.entities.RecognitionOutcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLogsScreen(
    logs: List<RecognitionLogEntity>,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    var filter by remember { mutableStateOf<RecognitionOutcome?>(null) }

    val filtered = remember(logs, filter) {
        if (filter == null) logs else logs.filter { it.outcome == filter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            )
        }
    ) { padding ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filter == RecognitionOutcome.RECOGNIZED,
                    onClick = { filter = RecognitionOutcome.RECOGNIZED },
                    label = { Text("Recognized") }
                )
                FilterChip(
                    selected = filter == RecognitionOutcome.UNKNOWN,
                    onClick = { filter = RecognitionOutcome.UNKNOWN },
                    label = { Text("Unknown") }
                )
                FilterChip(
                    selected = filter == RecognitionOutcome.NO_FACE,
                    onClick = { filter = RecognitionOutcome.NO_FACE },
                    label = { Text("No face") }
                )
            }

            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Text("No logs yet.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { log ->
                        LogCard(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: RecognitionLogEntity) {
    val time = remember(log.ts) { formatTs(log.ts) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(time, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(log.outcome.name, style = MaterialTheme.typography.titleMedium)
            if (log.bestScore != null) Text("score: ${"%.3f".format(log.bestScore)}")
            if (!log.personId.isNullOrBlank()) Text("personId: ${log.personId}")
        }
    }
}

private fun formatTs(ts: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(ts))
}
