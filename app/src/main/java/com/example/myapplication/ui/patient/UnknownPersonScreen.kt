package com.example.myapplication.ui.patient

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownPersonScreen(
    onHelpMeRemember: () -> Unit,
    onCallCaregiver: () -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        var localTts: TextToSpeech? = null

        localTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                localTts?.language = Locale.getDefault()
                localTts?.speak(
                    "I do not recognize this person.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "unknown_person"
                )
            }
        }

        onDispose {
            localTts?.stop()
            localTts?.shutdown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MemAid") }) }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                "I don’t recognize this person.",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "That’s okay.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onHelpMeRemember,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Help me remember")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCallCaregiver,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Call caregiver")
            }
        }
    }
}