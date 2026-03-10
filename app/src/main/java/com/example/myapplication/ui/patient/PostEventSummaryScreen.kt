package com.example.myapplication.ui.patient

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEventSummaryScreen(
    summaryText: String,
    onDone: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(8000)
        onDone()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MemAid") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Here is a short summary.", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text(summaryText, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}