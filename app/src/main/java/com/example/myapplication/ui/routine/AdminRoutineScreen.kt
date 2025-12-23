package com.example.myapplication.ui.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.entities.RepeatRule
import com.example.myapplication.data.entities.RoutineItemEntity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRoutineScreen(
    allItems: List<RoutineItemEntity>,
    onBack: () -> Unit,
    onAdd: (label: String, timeMinutes: Int, rule: RepeatRule, date: String?) -> Unit,
    onToggle: (RoutineItemEntity, Boolean) -> Unit,
    onDelete: (RoutineItemEntity) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("9") }
    var minute by remember { mutableStateOf("00") }
    var rule by remember { mutableStateOf(RepeatRule.DAILY) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routine (Admin)") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
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
            Text("Add routine item", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (e.g., Take medicine)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = hour,
                    onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                    label = { Text("Hour (0-23)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = minute,
                    onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                    label = { Text("Min (0-59)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(8.dp))

            RepeatRuleDropdown(
                selected = rule,
                onSelected = { rule = it }
            )

            if (rule == RepeatRule.NONE) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.trim() },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val h = (hour.toIntOrNull() ?: 9).coerceIn(0, 23)
                    val m = (minute.toIntOrNull() ?: 0).coerceIn(0, 59)
                    val timeMinutes = h * 60 + m

                    val d = if (rule == RepeatRule.NONE) date else null

                    if (label.isNotBlank()) {
                        onAdd(label.trim(), timeMinutes, rule, d)
                        label = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add") }

            Spacer(Modifier.height(16.dp))
            Text("All items", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (allItems.isEmpty()) {
                Text("No routine items yet.", style = MaterialTheme.typography.bodyLarge)
            } else {
                LazyColumn {
                    items(allItems) { item ->
                        ListItem(
                            headlineContent = { Text(item.label) },
                            supportingContent = {
                                Text(buildString {
                                    append("Time: ${formatTime(item.timeMinutes)} • ")
                                    append("Repeat: ${item.repeatRule}")
                                    if (item.repeatRule == RepeatRule.NONE) {
                                        append(" • Date: ${item.date ?: "-"}")
                                    }
                                })
                            },
                            trailingContent = {
                                Row {
                                    Switch(
                                        checked = item.enabled,
                                        onCheckedChange = { onToggle(item, it) }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = { onDelete(item) }) { Text("Delete") }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RepeatRuleDropdown(
    selected: RepeatRule,
    onSelected: (RepeatRule) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Repeat: ${selected.name}")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            RepeatRule.entries.forEach { r ->
                DropdownMenuItem(
                    text = { Text(r.name) },
                    onClick = {
                        onSelected(r)
                        expanded = false
                    }
                )
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
