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

    suspend fun getPersonById(personId: String): PersonEntity? = personDao.getById(personId)

    suspend fun getGalleryForPerson(personId: String): List<GalleryEntity> =
        galleryDao.listForPerson(personId)

    suspend fun getPhotoCount(personId: String): Int =
        galleryDao.listForPerson(personId).size

    suspend fun getVectorCount(personId: String): Int =
        vectorDao.vectorsForPerson(personId).size

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

    suspend fun deletePendingPerson(personId: String): Boolean {
        val person = personDao.getById(personId) ?: return false
        if (person.status != PersonStatus.PENDING) return false
        personDao.delete(person)
        return true
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

    suspend fun mergePendingIntoExisting(
        appContext: Context,
        pendingPersonId: String,
        existingPersonId: String
    ): Boolean = withContext(Dispatchers.Default) {

        if (pendingPersonId == existingPersonId) return@withContext false

        val pendingPerson = personDao.getById(pendingPersonId) ?: return@withContext false
        val existingPerson = personDao.getById(existingPersonId) ?: return@withContext false

        if (pendingPerson.status != PersonStatus.PENDING) return@withContext false
        if (existingPerson.status != PersonStatus.ACTIVE) return@withContext false

        val pendingPhotos = galleryDao.listForPerson(pendingPersonId)
        if (pendingPhotos.isEmpty()) return@withContext false

        val movedGallery = pendingPhotos.map { photo ->
            photo.copy(
                galleryId = java.util.UUID.randomUUID().toString(),
                personId = existingPersonId,
                ts = System.currentTimeMillis()
            )
        }

        galleryDao.insertAll(movedGallery)

        val vectors = buildVectorsFromPaths(
            context = appContext,
            personId = existingPersonId,
            imagePaths = pendingPhotos.map { it.imagePath },
            alreadyFaceCrops = false
        )

        if (vectors.isNotEmpty()) {
            vectorDao.insertAll(vectors)
        }

        personDao.delete(pendingPerson)

        true
    }
    suspend fun updatePersonBasics(
        personId: String,
        name: String,
        relation: String
    ): Boolean {
        val person = personDao.getById(personId) ?: return false
        personDao.upsert(
            person.copy(
                name = name.trim(),
                relation = relation.trim()
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

        val usable = filterUsablePhotoPaths(
            context = appContext,
            imagePaths = imagePaths
        )

        if (usable.isEmpty()) return@withContext 0

        val galleryItems = usable.map { path ->
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
            imagePaths = usable,
            alreadyFaceCrops = false
        )

        if (vectors.isNotEmpty()) {
            vectorDao.insertAll(vectors)
        }

        vectors.size
    }

    suspend fun appendConfirmedFaceCrops(
        appContext: Context,
        personId: String,
        cropPaths: List<String>
    ): Int = withContext(Dispatchers.Default) {
        if (cropPaths.isEmpty()) return@withContext 0

        val unique = cropPaths.distinct().take(2)
        val vectors = buildVectorsFromPaths(
            context = appContext,
            personId = personId,
            imagePaths = unique,
            alreadyFaceCrops = true
        )

        if (vectors.isEmpty()) return@withContext 0

        val galleryItems = vectors.indices.map { index ->
            GalleryEntity(
                personId = personId,
                imagePath = unique[index.coerceAtMost(unique.lastIndex)],
                pose = "auto-confirmed",
                lighting = null,
                quality = vectors[index].quality
            )
        }

        galleryDao.insertAll(galleryItems)
        vectorDao.insertAll(vectors)
        vectors.size
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

    private suspend fun filterUsablePhotoPaths(
        context: Context,
        imagePaths: List<String>
    ): List<String> = withContext(Dispatchers.Default) {
        val usable = mutableListOf<String>()

        for (path in imagePaths) {
            val bmp = ImageBitmapUtils.decodeUprightBitmap(path) ?: continue
            val rect = FaceCropper.detectLargestFace(bmp) ?: continue
            val crop = FaceCropper.cropSquare(bmp, rect) ?: continue
            val q = FaceQuality.evaluate(crop)

            val ok = q.brightness in 25f..235f && q.sharpness >= 3.5f
            if (ok) usable += path
        }

        usable
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
                    val rect = FaceCropper.detectLargestFace(bmp)
                    if (rect == null) {
                        Log.d("Enrollment", "[$index] skipped: no face")
                        continue
                    }

                    FaceCropper.cropSquare(bmp, rect).also {
                        if (it == null) {
                            Log.d("Enrollment", "[$index] skipped: crop failed")
                        }
                    } ?: continue
                }

                val quality = FaceQuality.evaluate(faceBitmap)
                val goodEnough =
                    quality.brightness in 25f..235f &&
                            quality.sharpness >= 3.5f

                if (!goodEnough) {
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