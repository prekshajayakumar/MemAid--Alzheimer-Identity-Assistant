package com.example.myapplication.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.util.CaregiverPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var phone by remember { mutableStateOf(CaregiverPrefs.getPhone(ctx) ?: "") }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
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
            Text("Caregiver Phone Number", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone (e.g. +91XXXXXXXXXX)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    CaregiverPrefs.setPhone(ctx, phone.trim())
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            if (saved) {
                Spacer(Modifier.height(8.dp))
                Text("Saved", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
