package com.example.myapplication.data.repo

import android.content.Context
import android.graphics.BitmapFactory
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.FaceVectorEntity
import com.example.myapplication.data.entities.PersonEntity
import com.example.myapplication.data.entities.PersonStatus
import com.example.myapplication.ml.EmbeddingCodec
import com.example.myapplication.ml.FaceCropper
import com.example.myapplication.ml.FaceEmbedder
import kotlinx.coroutines.flow.Flow
import java.io.File

class PeopleRepository(
    private val db: AppDb
) {
    private val personDao = db.personDao()
    private val galleryDao = db.galleryDao()
    private val vectorDao = db.vectorDao()

    fun allPeople(): Flow<List<PersonEntity>> = personDao.observeAll()
    fun pending(): Flow<List<PersonEntity>> = personDao.observeByStatus(PersonStatus.PENDING)

    suspend fun addPending(name: String, relation: String): String {
        val p = PersonEntity(name = name, relation = relation, status = PersonStatus.PENDING)
        personDao.upsert(p)
        return p.personId
    }

    suspend fun approve(personId: String) {
        val current = personDao.getById(personId) ?: return
        personDao.upsert(current.copy(status = PersonStatus.ACTIVE))
    }

    suspend fun addActive(name: String, relation: String) {
        personDao.upsert(PersonEntity(name = name, relation = relation, status = PersonStatus.ACTIVE))
    }

    suspend fun createPendingFromPhotoPaths(imagePaths: List<String>): String {
        val person = PersonEntity(
            name = null,
            relation = null,
            status = PersonStatus.PENDING
        )

        personDao.upsert(person)

        val galleryItems = imagePaths.map { path ->
            com.example.myapplication.data.entities.GalleryEntity(
                personId = person.personId,
                imagePath = path,
                pose = null,
                lighting = null,
                quality = 0f
            )
        }

        galleryDao.insertAll(galleryItems)
        return person.personId
    }

    // ✅ RESEARCH-GRADE: approve + create embeddings for all saved photos
    suspend fun approvePendingWithEmbeddings(
        appContext: Context,
        personId: String,
        name: String,
        relation: String
    ) {
        val current = personDao.getById(personId) ?: return

        // 1) Update identity fields + activate
        personDao.upsert(
            current.copy(
                name = name.trim(),
                relation = relation.trim(),
                status = PersonStatus.ACTIVE
            )
        )

        // 2) Load all gallery photos for this person
        val photos = galleryDao.listForPerson(personId)
        if (photos.isEmpty()) return

        val embedder = FaceEmbedder(appContext)

        // 3) For each photo: detect face → crop → embed → store vector
        val vectors = mutableListOf<FaceVectorEntity>()

        for (p in photos) {
            val file = File(p.imagePath)
            if (!file.exists()) continue

            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: continue

            val faceRect = FaceCropper.detectLargestFace(bmp) ?: continue
            val cropped = FaceCropper.crop(bmp, faceRect) ?: continue

            val emb = embedder.embed(cropped)
            val bytes = EmbeddingCodec.toByteArray(emb)

            vectors.add(
                FaceVectorEntity(
                    personId = personId,
                    embedding = bytes,
                    quality = 1.0f
                )
            )
        }

        if (vectors.isNotEmpty()) {
            vectorDao.insertAll(vectors)
        }
    }

    // keep old approvePending if you want, but use approvePendingWithEmbeddings going forward
    suspend fun approvePending(personId: String, name: String, relation: String) {
        val current = personDao.getById(personId) ?: return
        personDao.upsert(
            current.copy(
                name = name,
                relation = relation,
                status = PersonStatus.ACTIVE
            )
        )
    }
}
