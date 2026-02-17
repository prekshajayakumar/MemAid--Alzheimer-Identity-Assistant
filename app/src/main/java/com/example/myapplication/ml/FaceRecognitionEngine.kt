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

    data class RecognitionResult(
        val personId: String?,
        val bestScore: Float
    )

    suspend fun recognize(faceBitmap: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {

        val query = embedder.embed(faceBitmap)

        val allVectors = db.vectorDao().allVectors()
        if (allVectors.isEmpty()) return@withContext RecognitionResult(null, 0f)

        var bestScore = Float.NEGATIVE_INFINITY
        var bestPersonId: String? = null

        for (v in allVectors) {
            val stored = EmbeddingCodec.fromByteArray(v.embedding)
            val score = FaceMatcher.cosineSimilarity(query, stored)

            if (score > bestScore) {
                bestScore = score
                bestPersonId = v.personId
            }
        }

        if (bestScore >= THRESHOLD) RecognitionResult(bestPersonId, bestScore)
        else RecognitionResult(null, bestScore)
    }
}
