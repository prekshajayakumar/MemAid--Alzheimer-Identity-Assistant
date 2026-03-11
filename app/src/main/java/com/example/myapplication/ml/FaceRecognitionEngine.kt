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

    private val THRESHOLD = 0.62f
    private val MIN_MARGIN = 0.04f
    private val TOP_K = 3

    data class RecognitionResult(
        val personId: String?,
        val bestScore: Float
    )

    suspend fun recognize(faceBitmap: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {

        val quality = FaceQuality.evaluate(faceBitmap)

        if (!quality.accepted) {
            Log.d("FaceRecognition", "Rejected face due to quality")
            return@withContext RecognitionResult(null, 0f)
        }

        val query = embedder.embed(faceBitmap)

        val allVectors = db.vectorDao().allVectors()

        if (allVectors.isEmpty()) {
            Log.d("FaceRecognition", "No embeddings stored")
            return@withContext RecognitionResult(null, 0f)
        }

        val scoresByPerson = mutableMapOf<String, MutableList<Float>>()

        for (v in allVectors) {

            val stored = EmbeddingCodec.fromByteArray(v.embedding)

            val score = FaceMatcher.cosineSimilarity(query, stored)

            scoresByPerson.getOrPut(v.personId) { mutableListOf() }.add(score)
        }

        val personScores = scoresByPerson.mapValues { entry ->
            val top = entry.value.sortedDescending().take(TOP_K)
            top.average().toFloat()
        }

        val ranked = personScores.entries.sortedByDescending { it.value }

        val best = ranked.first()
        val secondScore = ranked.getOrNull(1)?.value ?: Float.NEGATIVE_INFINITY

        val margin = best.value - secondScore

        Log.d(
            "FaceRecognition",
            "best=${best.key} score=${best.value} margin=$margin"
        )

        if (best.value >= THRESHOLD && margin >= MIN_MARGIN) {
            RecognitionResult(best.key, best.value)
        } else {
            RecognitionResult(null, best.value)
        }
    }
}