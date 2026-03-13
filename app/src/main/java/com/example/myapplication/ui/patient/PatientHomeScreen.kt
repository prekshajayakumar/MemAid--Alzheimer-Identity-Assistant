package com.example.myapplication.ui.patient

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.entities.RoutineItemEntity

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    currentRoutine: RoutineItemEntity?,
    nextRoutine: RoutineItemEntity?,
    onRecognizePerson: () -> Unit,
    onCallCaregiver: () -> Unit,
    onTodaySummary: () -> Unit,
    onOpenAdminForNow: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MemAid",
                        modifier = Modifier
                            .padding(4.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onOpenAdminForNow() }
                            )
                    )
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Today", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {

                    when {
                        currentRoutine != null -> {
                            Text("Now", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(currentRoutine.label, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${formatTime(currentRoutine.timeMinutes)} - ${
                                    formatTime(
                                        currentRoutine.endTimeMinutes ?: (currentRoutine.timeMinutes + 60)
                                    )
                                }",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            currentRoutine.expectedLocationLabel?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(6.dp))
                                Text("Place: $it", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        nextRoutine != null -> {
                            Text("Next", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(nextRoutine.label, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${formatTime(nextRoutine.timeMinutes)} - ${
                                    formatTime(
                                        nextRoutine.endTimeMinutes ?: (nextRoutine.timeMinutes + 60)
                                    )
                                }",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            nextRoutine.expectedLocationLabel?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(6.dp))
                                Text("Place: $it", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        else -> {
                            Text(
                                "No activities scheduled right now.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onRecognizePerson,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text("Recognize Person")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onTodaySummary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("What have I done today?")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCallCaregiver,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Call Caregiver")
            }
        }
    }
}

private fun formatTime(minutes: Int): String {
    val h24 = (minutes / 60) % 24
    val m = minutes % 60

    val ampm = if (h24 < 12) "AM" else "PM"
    val h12 = when (val v = h24 % 12) {
        0 -> 12
        else -> v
    }
    val mm = if (m < 10) "0$m" else "$m"

    return "$h12:$mm $ampm"
}