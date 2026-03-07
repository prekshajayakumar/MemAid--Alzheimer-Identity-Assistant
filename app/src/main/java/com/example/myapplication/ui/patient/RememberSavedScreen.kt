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
fun RememberSavedScreen(
    onBackHome: () -> Unit,
    onCallCaregiver: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(6000)
        onBackHome()
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
                "I saved this person for review.",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Your caregiver can name this person later.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onBackHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Back home")
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