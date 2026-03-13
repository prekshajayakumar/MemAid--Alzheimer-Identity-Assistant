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
import com.example.myapplication.data.entities.GalleryEntity
import com.example.myapplication.data.entities.PersonEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPersonDetailScreen(
    person: PersonEntity,
    gallery: List<GalleryEntity>,
    vectorCount: Int,
    onSaveBasics: (name: String, relation: String) -> Unit,
    onAddMorePhotos: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember(person.personId, person.name) { mutableStateOf(person.name ?: "") }
    var relation by remember(person.personId, person.relation) { mutableStateOf(person.relation ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Person Details") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("Relation") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Button(
                    onClick = { onSaveBasics(name.trim(), relation.trim()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
            }

            item {
                OutlinedButton(
                    onClick = onAddMorePhotos,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add more photos")
                }
            }

            item {
                Text("Photos: ${gallery.size}", style = MaterialTheme.typography.bodyLarge)
                Text("Embeddings: $vectorCount", style = MaterialTheme.typography.bodyLarge)
            }

            items(gallery) { item ->
                val bitmap = remember(item.imagePath) {
                    BitmapFactory.decodeFile(item.imagePath)?.asImageBitmap()
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Saved photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text("Could not load image.")
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Path: ${item.imagePath}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}