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
fun UnknownPersonScreen(
    onHelpMeRemember: () -> Unit,
    onCallCaregiver: () -> Unit
) {

    var interacted by remember { mutableStateOf(false) }

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
                onClick = {
                    interacted = true
                    onHelpMeRemember()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Help me remember")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    interacted = true
                    onCallCaregiver()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Call caregiver")
            }
        }
    }
}