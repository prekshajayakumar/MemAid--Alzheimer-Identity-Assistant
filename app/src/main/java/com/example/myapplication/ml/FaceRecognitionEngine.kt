package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.myapplication.data.db.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class FaceRecognitionEngine(context: Context) {

    private val appContext = context.applicationContext
    private val embedder = FaceEmbedder(appContext)
    private val db = AppDb.get(appContext)

    // Keep as const for now; later you’ll tune via ThresholdExperiment + store best in prefs
    private val THRESHOLD = 0.80f

    suspend fun recognize(faceBitmap: Bitmap): String? = withContext(Dispatchers.Default) {

        val query = l2Normalize(embedder.embed(faceBitmap))

        val allVectors = db.vectorDao().allVectors()
        if (allVectors.isEmpty()) return@withContext null

        var bestScore = Float.NEGATIVE_INFINITY
        var bestPersonId: String? = null

        for (v in allVectors) {
            val stored = l2Normalize(EmbeddingCodec.fromByteArray(v.embedding))

            val score = cosine(query, stored)

            // Debug logging only (optional): remove in release builds
            Log.d("FaceMatch", "person=${v.personId} score=$score")

            if (score > bestScore) {
                bestScore = score
                bestPersonId = v.personId
            }
        }

        Log.d("FaceMatch", "BEST score=$bestScore bestPersonId=$bestPersonId")

        if (bestScore >= THRESHOLD) bestPersonId else null
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        // If both are L2 normalized: cosine = dot(a,b)
        var dot = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) dot += a[i] * b[i]
        return dot
    }

    private fun l2Normalize(x: FloatArray, eps: Float = 1e-12f): FloatArray {
        var sumSq = 0f
        for (v in x) sumSq += v * v
        val norm = sqrt(sumSq).coerceAtLeast(eps)

        val out = FloatArray(x.size)
        for (i in x.indices) out[i] = x[i] / norm
        return out
    }
}
