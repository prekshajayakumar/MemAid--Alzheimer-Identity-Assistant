package com.example.myapplication.ui.routine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.clickable
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
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
    onOpenItem: (RoutineItemEntity) -> Unit
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
    var isFetchingLocation by remember { mutableStateOf(false) }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Add routine item", style = MaterialTheme.typography.titleMedium)
            }

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
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !isFetchingLocation,
                        onClick = {
                            if (locationPermissions.permissions.none { it.status.isGranted }) {
                                locationPermissions.launchMultiplePermissionRequest()
                                message = "Please grant location permission, then tap again."
                            } else {
                                isFetchingLocation = true
                                message = "Fetching current location…"
                                fetchCurrentLocation(
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
                            }
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
                RepeatRuleDropdown(
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
                        val rad = radius.toFloatOrNull() ?: 150f

                        if (label.isBlank()) {
                            message = "Please enter an activity label."
                            return@Button
                        }

                        if (rule == RepeatRule.NONE && d.isNullOrBlank()) {
                            message = "Please enter a date."
                            return@Button
                        }

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
                        rule = RepeatRule.DAILY
                        date = LocalDate.now().toString()
                        startHour = "9"
                        startMinute = "00"
                        endHour = "10"
                        endMinute = "00"
                        message = "Routine item added."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add")
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("All items", style = MaterialTheme.typography.titleMedium)
            }

            if (allItems.isEmpty()) {
                item {
                    Text("No routine items yet.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                items(allItems, key = { it.routineId }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenItem(item) }
                    ) {
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
                                Column {
                                    Switch(
                                        checked = item.enabled,
                                        onCheckedChange = { onToggle(item, it) }
                                    )
                                    TextButton(onClick = { onDelete(item) }) {
                                        Text("Delete")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(
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