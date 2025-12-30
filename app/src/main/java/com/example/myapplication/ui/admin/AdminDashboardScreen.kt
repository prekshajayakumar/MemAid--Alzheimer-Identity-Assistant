package com.example.myapplication.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AdminDashboardScreen(
    onPeople: () -> Unit,
    onRoutine: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Admin Dashboard", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))

        Button(onClick = onPeople, modifier = Modifier.fillMaxWidth()) {
            Text("People (Pending Review)")
        }
        Spacer(Modifier.height(12.dp))

        Button(onClick = onRoutine, modifier = Modifier.fillMaxWidth()) {
            Text("Routine")
        }
        Spacer(Modifier.height(12.dp))

        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Settings")
        }
        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("Exit Admin")
        }
    }
}
