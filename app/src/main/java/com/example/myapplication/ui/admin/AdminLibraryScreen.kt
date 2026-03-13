package com.example.myapplication.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.entities.PersonEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLibraryScreen(
    people: List<PersonEntity>,
    photoCountByPersonId: Map<String, Int>,
    vectorCountByPersonId: Map<String, Int>,
    onOpenPerson: (String) -> Unit,
    onAddMorePhotos: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People Library") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        if (people.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text("No approved people yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(people) { person ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = person.name ?: "Unnamed",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Relation: ${person.relation ?: "—"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Photos: ${photoCountByPersonId[person.personId] ?: 0}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Embeddings: ${vectorCountByPersonId[person.personId] ?: 0}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = { onOpenPerson(person.personId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View Details")
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { onAddMorePhotos(person.personId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add more photos")
                            }
                        }
                    }
                }
            }
        }
    }
}