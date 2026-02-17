package com.example.myapplication.ui.patient

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognizedPersonScreen(
    name: String,
    relation: String?,
    onDone: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(3000)
        onDone()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MemAid") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    relation?.takeIf { it.isNotBlank() }?.let { "Relation: $it" } ?: "Relation: —",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                Text("Returning home…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
