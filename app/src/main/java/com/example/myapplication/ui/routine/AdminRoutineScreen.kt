package com.example.myapplication.ui.routine

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.entities.RepeatRule
import com.example.myapplication.data.entities.RoutineItemEntity
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AdminRoutineScreen(
    allItems: List<RoutineItemEntity>,
    onBack: () -> Unit,
    onAdd: (
        label: String,
        timeMinutes: Int,
        rule: RepeatRule,
        date: String?,
        endTimeMinutes: Int?,
        expectedLocationLabel: String?,
        expectedLatitude: Double?,
        expectedLongitude: Double?,
        allowedRadiusMeters: Float?
    ) -> Unit,
    onToggle: (RoutineItemEntity, Boolean) -> Unit,
    onDelete: (RoutineItemEntity) -> Unit,
) {
    val context = LocalContext.current
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    var label by remember { mutableStateOf("") }

    var startHour by remember { mutableStateOf("9") }
    var startMinute by remember { mutableStateOf("00") }

    var endHour by remember { mutableStateOf("10") }
    var endMinute by remember { mutableStateOf("00") }

    var expectedLocation by remember { mutableStateOf("") }
    var expectedLatitude by remember { mutableStateOf<Double?>(null) }
    var expectedLongitude by remember { mutableStateOf<Double?>(null) }
    var radius by remember { mutableStateOf("150") }

    var rule by remember { mutableStateOf(RepeatRule.DAILY) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }

    var message by remember { mutableStateOf<String?>(null) }

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
                label = { Text("Activity label") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Text("Start time", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))

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

            Spacer(Modifier.height(12.dp))

            Text("End time", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))

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

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = expectedLocation,
                onValueChange = { expectedLocation = it },
                label = { Text("Expected place label") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = radius,
                onValueChange = { radius = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Allowed radius (meters)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (locationPermissions.permissions.none { it.status.isGranted }) {
                            locationPermissions.launchMultiplePermissionRequest()
                        } else {
                            fetchCurrentLocation(
                                context = context,
                                onLocation = { lat, lon ->
                                    expectedLatitude = lat
                                    expectedLongitude = lon
                                    message = "Current location saved."
                                },
                                onError = {
                                    message = it
                                }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Use current location")
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

            if (expectedLatitude != null && expectedLongitude != null) {
                Spacer(Modifier.height(8.dp))
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

            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message!!,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

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

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val sh = (startHour.toIntOrNull() ?: 9).coerceIn(0, 23)
                    val sm = (startMinute.toIntOrNull() ?: 0).coerceIn(0, 59)
                    val eh = (endHour.toIntOrNull() ?: sh).coerceIn(0, 23)
                    val em = (endMinute.toIntOrNull() ?: sm).coerceIn(0, 59)

                    val startTimeMinutes = sh * 60 + sm
                    val endTime = eh * 60 + em
                    val endTimeMinutes = if (endTime > startTimeMinutes) endTime else null

                    val d = if (rule == RepeatRule.NONE) date else null
                    val expectedPlace = expectedLocation.trim().ifBlank { null }
                    val rad = radius.toFloatOrNull()

                    if (label.isNotBlank()) {
                        onAdd(
                            label.trim(),
                            startTimeMinutes,
                            rule,
                            d,
                            endTimeMinutes,
                            expectedPlace,
                            expectedLatitude,
                            expectedLongitude,
                            rad
                        )

                        label = ""
                        expectedLocation = ""
                        expectedLatitude = null
                        expectedLongitude = null
                        radius = "150"
                        message = "Routine item added."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add")
            }

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
                                Text(
                                    buildString {
                                        append("Time: ${formatTime(item.timeMinutes)}")
                                        item.endTimeMinutes?.let {
                                            append(" - ${formatTime(it)}")
                                        }
                                        append(" • Repeat: ${item.repeatRule}")
                                        if (item.repeatRule == RepeatRule.NONE) {
                                            append(" • Date: ${item.date ?: "-"}")
                                        }
                                        if (!item.expectedLocationLabel.isNullOrBlank()) {
                                            append(" • Place: ${item.expectedLocationLabel}")
                                        }
                                        if (item.expectedLatitude != null && item.expectedLongitude != null) {
                                            append(" • GPS set")
                                        }
                                    }
                                )
                            },
                            trailingContent = {
                                Row {
                                    Switch(
                                        checked = item.enabled,
                                        onCheckedChange = { onToggle(item, it) }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = { onDelete(item) }) {
                                        Text("Delete")
                                    }
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

@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(
    context: android.content.Context,
    onLocation: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    client.lastLocation
        .addOnSuccessListener { location ->
            if (location != null) {
                onLocation(location.latitude, location.longitude)
            } else {
                onError("Could not get current location. Try again outdoors or with GPS enabled.")
            }
        }
        .addOnFailureListener {
            onError("Could not get current location.")
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