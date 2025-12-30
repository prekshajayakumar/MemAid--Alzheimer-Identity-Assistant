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

    // Phone
    var phone by remember { mutableStateOf(CaregiverPrefs.getPhone(ctx) ?: "") }
    var phoneMsg by remember { mutableStateOf<String?>(null) }

    // PIN
    var newPin by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // ---- Caregiver Phone ----
            Text("Caregiver Phone Number", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    phoneMsg = null
                },
                label = { Text("Phone (e.g. +91XXXXXXXXXX)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    CaregiverPrefs.setPhone(ctx, phone.trim())
                    phoneMsg = "Phone saved"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save phone") }

            if (phoneMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(phoneMsg!!, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ---- Admin PIN ----
            Text("Admin PIN", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = newPin,
                onValueChange = {
                    newPin = it.filter(Char::isDigit).take(4)
                    pinMsg = null
                },
                label = { Text("New 4-digit PIN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    if (newPin.length == 4) {
                        CaregiverPrefs.setPin(ctx, newPin)
                        pinMsg = "PIN updated"
                        newPin = ""
                    } else {
                        pinMsg = "PIN must be 4 digits"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Update PIN") }

            if (pinMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(pinMsg!!, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
