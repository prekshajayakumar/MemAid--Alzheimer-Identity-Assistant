package com.example.myapplication.ui.admin

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.entities.PersonEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPeopleScreen(
    pending: List<PersonEntity>,
    activePeople: List<PersonEntity>,
    photoPathByPersonId: Map<String, String>,
    photoCountByPersonId: Map<String, Int>,
    onCaptureMorePhotos: (personId: String) -> Unit,
    onApprove: (personId: String, name: String, relation: String) -> Unit,
    onMergeIntoExisting: (pendingPersonId: String, existingPersonId: String) -> Unit,
    onReject: (personId: String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending People") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        if (pending.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No pending people.")
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pending) { person ->
                    PendingPersonCard(
                        person = person,
                        activePeople = activePeople,
                        photoPath = photoPathByPersonId[person.personId],
                        photoCount = photoCountByPersonId[person.personId] ?: 0,
                        onCaptureMorePhotos = onCaptureMorePhotos,
                        onApprove = onApprove,
                        onMergeIntoExisting = onMergeIntoExisting,
                        onReject = onReject
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingPersonCard(
    person: PersonEntity,
    activePeople: List<PersonEntity>,
    photoPath: String?,
    photoCount: Int,
    onCaptureMorePhotos: (String) -> Unit,
    onApprove: (String, String, String) -> Unit,
    onMergeIntoExisting: (String, String) -> Unit,
    onReject: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedExistingId by remember { mutableStateOf<String?>(null) }

    val imageBitmap = remember(photoPath) {
        photoPath?.let { path ->
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }

    val selectedExistingName = activePeople
        .firstOrNull { it.personId == selectedExistingId }
        ?.name
        ?: ""

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("New Person", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Pending person photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    "No photo preview available.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Photos captured: $photoCount",
                style = MaterialTheme.typography.bodyMedium
            )

            if (photoCount < 2) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "One photo is enough. More photos improve recognition.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { onCaptureMorePhotos(person.personId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Capture more photos")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = relation,
                onValueChange = { relation = it },
                label = { Text("Relation") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                enabled = name.isNotBlank() && relation.isNotBlank() && photoCount >= 1,
                onClick = {
                    onApprove(person.personId, name.trim(), relation.trim())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Approve as New Person")
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Or link this to an existing person",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedExistingName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Existing person") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    activePeople.forEach { existing ->
                        DropdownMenuItem(
                            text = {
                                Text(existing.name ?: "Unnamed")
                            },
                            onClick = {
                                selectedExistingId = existing.personId
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                enabled = selectedExistingId != null,
                onClick = {
                    selectedExistingId?.let {
                        onMergeIntoExisting(person.personId, it)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add to Existing Person")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onReject(person.personId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reject / Delete")
            }
        }
    }
}