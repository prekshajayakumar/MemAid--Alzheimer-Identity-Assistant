package com.example.myapplication.ui.routine

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.entities.RepeatRule
import com.example.myapplication.data.entities.RoutineItemEntity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRoutineDetailScreen(
    item: RoutineItemEntity,
    onSave: (RoutineItemEntity) -> Unit,
    onDelete: (RoutineItemEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var label by remember(item.routineId) { mutableStateOf(item.label) }

    var startHour by remember(item.routineId) { mutableStateOf((item.timeMinutes / 60).toString()) }
    var startMinute by remember(item.routineId) {
        mutableStateOf((item.timeMinutes % 60).toString().padStart(2, '0'))
    }

    val endDefault = item.endTimeMinutes ?: (item.timeMinutes + 60)
    var endHour by remember(item.routineId) { mutableStateOf(((endDefault / 60) % 24).toString()) }
    var endMinute by remember(item.routineId) {
        mutableStateOf((endDefault % 60).toString().padStart(2, '0'))
    }

    var expectedLocation by remember(item.routineId) {
        mutableStateOf(item.expectedLocationLabel ?: "")
    }
    var expectedLatitude by remember(item.routineId) { mutableStateOf(item.expectedLatitude) }
    var expectedLongitude by remember(item.routineId) { mutableStateOf(item.expectedLongitude) }
    var radius by remember(item.routineId) {
        mutableStateOf((item.allowedRadiusMeters ?: 150f).toString())
    }

    var rule by remember(item.routineId) { mutableStateOf(item.repeatRule) }
    var date by remember(item.routineId) { mutableStateOf(item.date ?: "") }

    var message by remember { mutableStateOf<String?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Routine") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Activity label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Text("Start time", style = MaterialTheme.typography.labelLarge)
            }

            item {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { startHour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = startMinute,
                        onValueChange = { startMinute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            item {
                Text("End time", style = MaterialTheme.typography.labelLarge)
            }

            item {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = endHour,
                        onValueChange = { endHour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = endMinute,
                        onValueChange = { endMinute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = expectedLocation,
                    onValueChange = { expectedLocation = it },
                    label = { Text("Expected place label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = radius,
                    onValueChange = { radius = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Allowed radius (meters)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                RepeatRuleDetailDropdown(
                    selected = rule,
                    onSelected = { rule = it }
                )
            }

            if (rule == RepeatRule.NONE) {
                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it.trim() },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !isFetchingLocation,
                        onClick = {
                            isFetchingLocation = true
                            message = "Fetching current location…"
                            fetchDetailCurrentLocation(
                                context = context,
                                onLocation = { lat, lon ->
                                    expectedLatitude = lat
                                    expectedLongitude = lon
                                    if (expectedLocation.isBlank()) {
                                        expectedLocation = "Saved location"
                                    }
                                    message = "Current location saved."
                                    isFetchingLocation = false
                                },
                                onError = {
                                    message = it
                                    isFetchingLocation = false
                                }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isFetchingLocation) "Getting location…" else "Use current location")
                    }

                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = {
                            expectedLatitude = null
                            expectedLongitude = null
                            message = "Saved location cleared."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear location")
                    }
                }
            }

            if (expectedLatitude != null && expectedLongitude != null) {
                item {
                    Text(
                        text = "GPS set: ${
                            String.format(
                                Locale.US,
                                "%.5f, %.5f",
                                expectedLatitude,
                                expectedLongitude
                            )
                        }",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (message != null) {
                item {
                    Text(
                        text = message!!,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        val sh = (startHour.toIntOrNull() ?: 9).coerceIn(0, 23)
                        val sm = (startMinute.toIntOrNull() ?: 0).coerceIn(0, 59)
                        val eh = (endHour.toIntOrNull() ?: sh).coerceIn(0, 23)
                        val em = (endMinute.toIntOrNull() ?: sm).coerceIn(0, 59)

                        val startTimeMinutes = sh * 60 + sm
                        val endTime = eh * 60 + em
                        val endTimeMinutes = if (endTime > startTimeMinutes) endTime else null

                        val updated = item.copy(
                            label = label.trim(),
                            timeMinutes = startTimeMinutes,
                            endTimeMinutes = endTimeMinutes,
                            repeatRule = rule,
                            date = if (rule == RepeatRule.NONE) date.ifBlank { null } else null,
                            expectedLocationLabel = expectedLocation.trim().ifBlank { null },
                            expectedLatitude = expectedLatitude,
                            expectedLongitude = expectedLongitude,
                            allowedRadiusMeters = radius.toFloatOrNull() ?: 150f
                        )

                        onSave(updated)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Changes")
                }
            }

            item {
                OutlinedButton(
                    onClick = { onDelete(item) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Routine")
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RepeatRuleDetailDropdown(
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

@SuppressLint("MissingPermission")
private fun fetchDetailCurrentLocation(
    context: Context,
    onLocation: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val tokenSource = CancellationTokenSource()

    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
        .addOnSuccessListener { location ->
            if (location != null) {
                onLocation(location.latitude, location.longitude)
            } else {
                client.lastLocation
                    .addOnSuccessListener { last ->
                        if (last != null) {
                            onLocation(last.latitude, last.longitude)
                        } else {
                            onError("Could not get current location. Turn on GPS and try again.")
                        }
                    }
                    .addOnFailureListener {
                        onError("Could not get current location.")
                    }
            }
        }
        .addOnFailureListener {
            onError("Could not get current location.")
        }
}