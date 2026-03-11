package com.example.myapplication.data.repo

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.FaceVectorEntity
import com.example.myapplication.data.entities.GalleryEntity
import com.example.myapplication.data.entities.PersonEntity
import com.example.myapplication.data.entities.PersonStatus
import com.example.myapplication.ml.EmbeddingCodec
import com.example.myapplication.ml.FaceCropper
import com.example.myapplication.ml.FaceEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PeopleRepository(
    private val db: AppDb
) {
    private val personDao = db.personDao()
    private val galleryDao = db.galleryDao()
    private val vectorDao = db.vectorDao()

    fun allPeople(): Flow<List<PersonEntity>> = personDao.observeAll()
    fun pending(): Flow<List<PersonEntity>> = personDao.observeByStatus(PersonStatus.PENDING)

    suspend fun createPendingFromPhotoPaths(imagePaths: List<String>): String {
        val person = PersonEntity(
            name = null,
            relation = null,
            status = PersonStatus.PENDING
        )

        personDao.upsert(person)

        val galleryItems = imagePaths.map { path ->
            GalleryEntity(
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

    suspend fun addPhotosToPending(
        personId: String,
        imagePaths: List<String>
    ) {
        if (imagePaths.isEmpty()) return

        val items = imagePaths.map { path ->
            GalleryEntity(
                personId = personId,
                imagePath = path,
                pose = null,
                lighting = null,
                quality = 0f
            )
        }

        galleryDao.insertAll(items)
    }

    suspend fun approvePendingWithEmbeddings(
        appContext: Context,
        personId: String,
        name: String,
        relation: String
    ): Boolean {
        val current = personDao.getById(personId) ?: return false
        val stored = generateAndStoreEmbeddings(appContext, personId)

        Log.d("Enrollment", "personId=$personId storedVectors=$stored")

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
        if (gallery.isEmpty()) {
            Log.d("Enrollment", "No gallery images for personId=$personId")
            return@withContext 0
        }

        val embedder = FaceEmbedder(context.applicationContext)

        try {
            val vectors = mutableListOf<FaceVectorEntity>()

            for ((index, g) in gallery.withIndex()) {
                val bmp = BitmapFactory.decodeFile(g.imagePath)
                if (bmp == null) {
                    Log.d("Enrollment", "[$index] decode failed path=${g.imagePath}")
                    continue
                }

                val rect = FaceCropper.detectLargestFace(bmp)
                val faceBitmap = if (rect != null) {
                    FaceCropper.crop(bmp, rect) ?: bmp
                } else {
                    bmp
                }

                if (faceBitmap.width < 32 || faceBitmap.height < 32) {
                    Log.d("Enrollment", "[$index] skipped small image ${faceBitmap.width}x${faceBitmap.height}")
                    continue
                }

                val embedding = embedder.embed(faceBitmap)

                vectors.add(
                    FaceVectorEntity(
                        personId = personId,
                        embedding = EmbeddingCodec.toByteArray(embedding),
                        quality = 1f
                    )
                )

                Log.d("Enrollment", "[$index] vector created")
            }

            if (vectors.isEmpty()) {
                Log.d("Enrollment", "No usable vectors created for personId=$personId")
                return@withContext 0
            }

            vectorDao.deleteForPerson(personId)
            vectorDao.insertAll(vectors)

            Log.d("Enrollment", "Inserted ${vectors.size} vectors for personId=$personId")
            return@withContext vectors.size
        } finally {
            embedder.close()
        }
    }
}