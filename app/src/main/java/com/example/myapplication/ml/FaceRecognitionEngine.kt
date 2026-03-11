package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.myapplication.data.db.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FaceRecognitionEngine(context: Context) {

    private val appContext = context.applicationContext
    private val embedder = FaceEmbedder(appContext)
    private val db = AppDb.get(appContext)

    // DEADLINE MODE: relaxed threshold
    private val THRESHOLD = 0.45f

    data class RecognitionResult(
        val personId: String?,
        val bestScore: Float
    )

    suspend fun recognize(faceBitmap: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {
        val query = embedder.embed(faceBitmap)

        val allVectors = db.vectorDao().allVectors()
        if (allVectors.isEmpty()) {
            Log.d("FaceRecognition", "No embeddings stored")
            return@withContext RecognitionResult(null, 0f)
        }

        var bestScore = Float.NEGATIVE_INFINITY
        var bestPersonId: String? = null

        for (v in allVectors) {
            val stored = EmbeddingCodec.fromByteArray(v.embedding)
            val score = FaceMatcher.cosineSimilarity(query, stored)

            Log.d("FaceRecognition", "candidate=${v.personId} score=$score")

            if (score > bestScore) {
                bestScore = score
                bestPersonId = v.personId
            }
        }

        Log.d("FaceRecognition", "BEST person=$bestPersonId score=$bestScore threshold=$THRESHOLD")

        if (bestScore >= THRESHOLD) {
            RecognitionResult(bestPersonId, bestScore)
        } else {
            RecognitionResult(null, bestScore)
        }
    }
}