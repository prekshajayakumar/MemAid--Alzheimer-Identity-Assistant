package com.example.myapplication.data.repo

import android.content.Context
import android.graphics.BitmapFactory
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
import kotlin.math.sqrt

class PeopleRepository(
    private val db: AppDb
) {
    private val personDao = db.personDao()
    private val galleryDao = db.galleryDao()
    private val vectorDao = db.vectorDao()

    fun allPeople(): Flow<List<PersonEntity>> = personDao.observeAll()
    fun pending(): Flow<List<PersonEntity>> = personDao.observeByStatus(PersonStatus.PENDING)

    // Optional legacy
    suspend fun addPending(name: String, relation: String): String {
        val p = PersonEntity(name = name, relation = relation, status = PersonStatus.PENDING)
        personDao.upsert(p)
        return p.personId
    }

    // Patient flow: pending person with only photos (no identity)
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

    /**
     * Production-grade approval:
     * 1) generate + store embeddings (off main thread)
     * 2) mark ACTIVE with name + relation
     *
     * Reason: ensures we only activate a person after at least one usable vector exists.
     */
    suspend fun approvePendingWithEmbeddings(
        appContext: Context,
        personId: String,
        name: String,
        relation: String
    ) {
        val current = personDao.getById(personId) ?: return

        // 1) Generate/store vectors first (so ACTIVE implies “recognizable”)
        val stored = generateAndStoreEmbeddings(appContext, personId)

        // If no face vectors were produced, keep them PENDING (admin can retry with better photo)
        if (stored == 0) return

        // 2) Activate + set identity
        personDao.upsert(
            current.copy(
                name = name.trim(),
                relation = relation.trim(),
                status = PersonStatus.ACTIVE
            )
        )
    }

    /**
     * Returns number of vectors stored.
     * Clears old vectors first to avoid stale data.
     */
    private suspend fun generateAndStoreEmbeddings(
        context: Context,
        personId: String
    ): Int = withContext(Dispatchers.Default) {

        val gallery = galleryDao.listForPerson(personId)
        if (gallery.isEmpty()) return@withContext 0

        val embedder = FaceEmbedder(context)
        val vectors = mutableListOf<FaceVectorEntity>()

        for (g in gallery) {
            val bmp = BitmapFactory.decodeFile(g.imagePath) ?: continue

            val rect = FaceCropper.detectLargestFace(bmp) ?: continue
            val face = FaceCropper.crop(bmp, rect) ?: continue

            val embedding = embedder.embed(face)
            val norm = l2Normalize(embedding)
            vectors.add(
                FaceVectorEntity(
                        personId = personId,
                        embedding = EmbeddingCodec.toByteArray(norm),
                    quality = 1f
                )
            )
        }

        if (vectors.isEmpty()) return@withContext 0

        // Replace vectors atomically for this person (prevents duplicates)
        vectorDao.deleteForPerson(personId)
        vectorDao.insertAll(vectors)

        return@withContext vectors.size
    }

    private fun l2Normalize(x: FloatArray, eps: Float = 1e-12f): FloatArray {
        var sumSq = 0f
        for (v in x) sumSq += v * v
        val norm = kotlin.math.sqrt(sumSq).coerceAtLeast(eps)

        val out = FloatArray(x.size)
        for (i in x.indices) out[i] = x[i] / norm
        return out
    }
}
