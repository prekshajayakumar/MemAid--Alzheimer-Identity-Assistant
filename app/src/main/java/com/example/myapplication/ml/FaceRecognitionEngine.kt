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

    private val THRESHOLD = 0.80f

    suspend fun recognize(faceBitmap: Bitmap): String? = withContext(Dispatchers.Default) {

        val query = embedder.embed(faceBitmap)

        val allVectors = db.vectorDao().allVectors()
        if (allVectors.isEmpty()) return@withContext null

        var bestScore = Float.NEGATIVE_INFINITY
        var bestPersonId: String? = null

        for (v in allVectors) {
            val stored = EmbeddingCodec.fromByteArray(v.embedding)
            val score = FaceMatcher.cosineSimilarity(query, stored)

            Log.d("FaceMatch", "person=${v.personId} score=$score")

            if (score > bestScore) {
                bestScore = score
                bestPersonId = v.personId
            }
        }

        Log.d("FaceMatch", "BEST score=$bestScore bestPersonId=$bestPersonId")

        if (bestScore >= THRESHOLD) bestPersonId else null
    }
}
