package com.example.myapplication.ui.patient

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.entities.RoutineItemEntity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    todayItems: List<RoutineItemEntity>,
    onRecognizePerson: () -> Unit,
    onCallCaregiver: () -> Unit,
    onOpenAdminForNow: () -> Unit, // TEMP (we’ll hide/gate later)
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
                                onClick = { /* do nothing */ },
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
                if (todayItems.isEmpty()) {
                    Text(
                        text = "No items for today.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(todayItems) { item ->
                            ListItem(
                                headlineContent = { Text(formatTime(item.timeMinutes)) },
                                supportingContent = { Text(item.label) }
                            )
                            HorizontalDivider()
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
