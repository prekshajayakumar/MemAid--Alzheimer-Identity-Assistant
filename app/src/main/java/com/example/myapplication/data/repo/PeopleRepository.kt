package com.example.myapplication.data.repo

import android.content.Context
import android.util.Log
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.FaceVectorEntity
import com.example.myapplication.data.entities.GalleryEntity
import com.example.myapplication.data.entities.PersonEntity
import com.example.myapplication.data.entities.PersonStatus
import com.example.myapplication.ml.EmbeddingCodec
import com.example.myapplication.ml.FaceCropper
import com.example.myapplication.ml.FaceEmbedder
import com.example.myapplication.ml.FaceQuality
import com.example.myapplication.util.ImageBitmapUtils
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
    fun active(): Flow<List<PersonEntity>> = personDao.observeByStatus(PersonStatus.ACTIVE)

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
        val stored = regenerateEmbeddingsFromGallery(appContext, personId)

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

    suspend fun addPhotosToExistingPersonAndEmbed(
        appContext: Context,
        personId: String,
        imagePaths: List<String>
    ): Int = withContext(Dispatchers.Default) {
        if (imagePaths.isEmpty()) return@withContext 0

        val galleryItems = imagePaths.map { path ->
            GalleryEntity(
                personId = personId,
                imagePath = path,
                pose = null,
                lighting = null,
                quality = 0f
            )
        }
        galleryDao.insertAll(galleryItems)

        val vectors = buildVectorsFromPaths(
            context = appContext,
            personId = personId,
            imagePaths = imagePaths,
            alreadyFaceCrops = false
        )

        if (vectors.isNotEmpty()) {
            vectorDao.insertAll(vectors)
        }

        vectors.size
    }

    suspend fun appendConfirmedFaceCrop(
        appContext: Context,
        personId: String,
        cropPath: String
    ): Boolean = withContext(Dispatchers.Default) {
        val vectors = buildVectorsFromPaths(
            context = appContext,
            personId = personId,
            imagePaths = listOf(cropPath),
            alreadyFaceCrops = true
        )

        if (vectors.isEmpty()) return@withContext false

        galleryDao.insertAll(
            listOf(
                GalleryEntity(
                    personId = personId,
                    imagePath = cropPath,
                    pose = "auto-confirmed",
                    lighting = null,
                    quality = vectors.first().quality
                )
            )
        )
        vectorDao.insertAll(vectors)
        true
    }

    private suspend fun regenerateEmbeddingsFromGallery(
        context: Context,
        personId: String
    ): Int = withContext(Dispatchers.Default) {

        val gallery = galleryDao.listForPerson(personId)
        if (gallery.isEmpty()) {
            Log.d("Enrollment", "No gallery images for personId=$personId")
            return@withContext 0
        }

        val vectors = buildVectorsFromPaths(
            context = context,
            personId = personId,
            imagePaths = gallery.map { it.imagePath },
            alreadyFaceCrops = false
        )

        if (vectors.isEmpty()) {
            Log.d("Enrollment", "No good vectors created for personId=$personId")
            return@withContext 0
        }

        vectorDao.deleteForPerson(personId)
        vectorDao.insertAll(vectors)

        Log.d("Enrollment", "Inserted ${vectors.size} vectors for personId=$personId")
        vectors.size
    }

    private suspend fun buildVectorsFromPaths(
        context: Context,
        personId: String,
        imagePaths: List<String>,
        alreadyFaceCrops: Boolean
    ): List<FaceVectorEntity> = withContext(Dispatchers.Default) {
        val embedder = FaceEmbedder(context.applicationContext)

        try {
            val vectors = mutableListOf<FaceVectorEntity>()

            for ((index, path) in imagePaths.withIndex()) {
                val bmp = ImageBitmapUtils.decodeUprightBitmap(path)
                if (bmp == null) {
                    Log.d("Enrollment", "[$index] decode failed path=$path")
                    continue
                }

                val faceBitmap = if (alreadyFaceCrops) {
                    bmp
                } else {
                    val detected = FaceCropper.detectSingleUsableFace(bmp)
                    if (detected == null) {
                        Log.d("Enrollment", "[$index] skipped: no single usable face")
                        continue
                    }

                    FaceCropper.cropSquare(bmp, detected.boundingBox).also {
                        if (it == null) {
                            Log.d("Enrollment", "[$index] skipped: crop failed")
                        }
                    } ?: continue
                }

                val quality = FaceQuality.evaluate(faceBitmap)
                if (!quality.accepted) {
                    Log.d(
                        "Enrollment",
                        "[$index] skipped: quality rejected b=${quality.brightness} c=${quality.contrast} s=${quality.sharpness}"
                    )
                    continue
                }

                val embedding = embedder.embed(faceBitmap)

                vectors.add(
                    FaceVectorEntity(
                        personId = personId,
                        embedding = EmbeddingCodec.toByteArray(embedding),
                        quality = quality.sharpness
                    )
                )

                Log.d("Enrollment", "[$index] vector created")
            }

            vectors
        } finally {
            embedder.close()
        }
    }
}