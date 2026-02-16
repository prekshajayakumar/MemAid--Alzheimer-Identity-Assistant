package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.myapplication.data.db.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FaceRecognitionEngine(context: Context) {

    private val embedder = FaceEmbedder(context)
    private val db = AppDb.get(context)

    private val THRESHOLD = 0.80f   // conservative for Alzheimer use-case

    suspend fun recognize(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {

        val embedding = embedder.embed(bitmap)
        val allVectors = db.vectorDao().allVectors()

        var bestScore = 0f
        var bestPersonId: String? = null

        for (vector in allVectors) {
            val stored = EmbeddingCodec.fromByteArray(vector.embedding)
            val score = FaceMatcher.cosineSimilarity(embedding, stored)

            Log.d("FaceMatch", "person=${vector.personId} score=$score")

            if (score > bestScore) {
                bestScore = score
                bestPersonId = vector.personId
            }
        }

        Log.d("FaceMatch", "BEST score=$bestScore")

        if (bestScore >= THRESHOLD) bestPersonId else null
    }
}
