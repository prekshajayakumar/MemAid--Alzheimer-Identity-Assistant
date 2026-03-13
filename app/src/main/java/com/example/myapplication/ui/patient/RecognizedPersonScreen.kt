package com.example.myapplication.ui.patient

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognizedPersonScreen(
    name: String,
    relation: String?,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        var localTts: TextToSpeech? = null

        localTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                localTts?.language = Locale.getDefault()
                val spoken = relation?.takeIf { it.isNotBlank() }?.let {
                    "This is $name. Relation: $it."
                } ?: "This is $name."
                localTts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "recognized_person")
            }
        }

        onDispose {
            localTts?.stop()
            localTts?.shutdown()
        }
    }

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