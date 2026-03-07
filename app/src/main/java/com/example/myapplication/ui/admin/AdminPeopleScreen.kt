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
    photoPathByPersonId: Map<String, String>,
    onApprove: (personId: String, name: String, relation: String) -> Unit,
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
                        photoPath = photoPathByPersonId[person.personId],
                        onApprove = onApprove
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingPersonCard(
    person: PersonEntity,
    photoPath: String?,
    onApprove: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }

    val imageBitmap = remember(photoPath) {
        photoPath?.let { path ->
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }

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
                onClick = {
                    if (name.isNotBlank() && relation.isNotBlank()) {
                        onApprove(person.personId, name.trim(), relation.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Approve")
            }
        }
    }
}