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

    // Slightly more permissive per-shot, because burst aggregation will decide final result.
    private val THRESHOLD = 0.72f
    private val MIN_MARGIN = 0.04f
    private val TOP_K = 2

    data class RecognitionResult(
        val personId: String?,
        val bestScore: Float,
        val reason: String? = null
    )

    suspend fun recognizeFromPhoto(photoBitmap: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {
        val detected = FaceCropper.detectSingleUsableFace(photoBitmap)
            ?: return@withContext RecognitionResult(null, 0f, "No single usable face")

        val faceBitmap = FaceCropper.cropSquare(photoBitmap, detected.boundingBox)
            ?: return@withContext RecognitionResult(null, 0f, "Face crop failed")

        val quality = FaceQuality.evaluate(faceBitmap)
        if (!quality.accepted) {
            return@withContext RecognitionResult(null, 0f, "Face quality too low")
        }

        val query = embedder.embed(faceBitmap)

        val allVectors = db.vectorDao().allVectors()
        if (allVectors.isEmpty()) {
            Log.d("FaceRecognition", "No embeddings stored")
            return@withContext RecognitionResult(null, 0f, "No enrolled people")
        }

        val scoresByPerson = mutableMapOf<String, MutableList<Float>>()

        for (v in allVectors) {
            val stored = EmbeddingCodec.fromByteArray(v.embedding)
            val score = FaceMatcher.cosineSimilarity(query, stored)
            scoresByPerson.getOrPut(v.personId) { mutableListOf() }.add(score)
        }

        val ranked = scoresByPerson.map { (personId, scores) ->
            val sorted = scores.sortedDescending()
            val best = sorted.first()
            val avgTop = sorted.take(TOP_K).average().toFloat()
            val finalScore = (0.75f * best) + (0.25f * avgTop)
            personId to finalScore
        }.sortedByDescending { it.second }

        val best = ranked.firstOrNull()
            ?: return@withContext RecognitionResult(null, 0f, "No match candidates")

        val secondScore = ranked.getOrNull(1)?.second ?: Float.NEGATIVE_INFINITY
        val margin = best.second - secondScore

        Log.d(
            "FaceRecognition",
            "bestPerson=${best.first} bestScore=${best.second} secondScore=$secondScore margin=$margin threshold=$THRESHOLD"
        )

        if (best.second >= THRESHOLD && margin >= MIN_MARGIN) {
            RecognitionResult(best.first, best.second, null)
        } else {
            RecognitionResult(null, best.second, "Unknown")
        }
    }

    fun close() {
        embedder.close()
    }
}