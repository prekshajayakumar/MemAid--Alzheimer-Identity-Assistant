package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import com.example.myapplication.data.db.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FaceRecognitionEngine(context: Context) {

    private val appContext = context.applicationContext
    private val embedder = FaceEmbedder(appContext)
    private val db = AppDb.get(appContext)

    private val THRESHOLD = 0.80f
    private val SAFETY_FLOOR = 0.55f

    data class RecognitionResult(
        val personId: String?,
        val bestScore: Float
    )

    suspend fun recognize(faceBitmap: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {

        val query = embedder.embed(faceBitmap)

        val allVectors = db.vectorDao().allVectors()
        if (allVectors.isEmpty()) return@withContext RecognitionResult(null, 0f)

        val scoresByPerson = mutableMapOf<String, Float>()

        for (v in allVectors) {
            val stored = EmbeddingCodec.fromByteArray(v.embedding)
            val score = FaceMatcher.cosineSimilarity(query, stored)

            val previous = scoresByPerson[v.personId]
            if (previous == null || score > previous) {
                scoresByPerson[v.personId] = score
            }
        }

        val best = scoresByPerson.maxByOrNull { it.value }
            ?: return@withContext RecognitionResult(null, 0f)

        val bestPersonId = best.key
        val bestScore = best.value

        if (bestScore < SAFETY_FLOOR) {
            return@withContext RecognitionResult(null, bestScore)
        }

        if (bestScore >= THRESHOLD) {
            RecognitionResult(bestPersonId, bestScore)
        } else {
            RecognitionResult(null, bestScore)
        }
    }
}