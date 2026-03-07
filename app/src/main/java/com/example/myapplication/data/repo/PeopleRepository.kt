package com.example.myapplication.data.repo

import android.content.Context
import android.graphics.BitmapFactory
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.*
import com.example.myapplication.ml.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PeopleRepository(
    private val db: AppDb
) {

    private val personDao = db.personDao()
    private val galleryDao = db.galleryDao()
    private val vectorDao = db.vectorDao()

    fun allPeople(): Flow<List<PersonEntity>> =
        personDao.observeAll()

    fun pending(): Flow<List<PersonEntity>> =
        personDao.observeByStatus(PersonStatus.PENDING)

    suspend fun createPendingFromPhotoPaths(
        imagePaths: List<String>
    ): String {

        val person = PersonEntity(
            name = null,
            relation = null,
            status = PersonStatus.PENDING
        )

        personDao.upsert(person)

        val galleryItems = imagePaths.map {

            GalleryEntity(
                personId = person.personId,
                imagePath = it,
                pose = null,
                lighting = null,
                quality = 0f
            )
        }

        galleryDao.insertAll(galleryItems)

        return person.personId
    }

    suspend fun approvePendingWithEmbeddings(
        appContext: Context,
        personId: String,
        name: String,
        relation: String
    ): Boolean {

        val current = personDao.getById(personId) ?: return false

        val stored = generateAndStoreEmbeddings(
            appContext,
            personId
        )

        if (stored == 0) return false

        personDao.upsert(
            current.copy(
                name = name.trim(),
                relation = relation.trim(),
                status = PersonStatus.ACTIVE
            )
        )

        return true
    }

    private suspend fun generateAndStoreEmbeddings(
        context: Context,
        personId: String
    ): Int = withContext(Dispatchers.Default) {

        val gallery = galleryDao.listForPerson(personId)

        if (gallery.isEmpty()) return@withContext 0

        val embedder = FaceEmbedder(context.applicationContext)

        try {

            val vectors = mutableListOf<FaceVectorEntity>()

            for (g in gallery) {

                val bmp = BitmapFactory.decodeFile(g.imagePath)
                    ?: continue

                val faceBitmap = run {

                    val rect = FaceCropper.detectLargestFace(bmp)

                    if (rect != null) {
                        FaceCropper.crop(bmp, rect) ?: bmp
                    } else {
                        bmp
                    }
                }

                val embedding = embedder.embed(faceBitmap)

                vectors.add(
                    FaceVectorEntity(
                        personId = personId,
                        embedding = EmbeddingCodec.toByteArray(embedding),
                        quality = 1f
                    )
                )
            }

            if (vectors.isEmpty()) return@withContext 0

            vectorDao.deleteForPerson(personId)
            vectorDao.insertAll(vectors)

            vectors.size
        } finally {
            embedder.close()
        }
    }
}