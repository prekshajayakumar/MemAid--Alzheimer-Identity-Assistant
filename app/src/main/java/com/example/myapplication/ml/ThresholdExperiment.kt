package com.example.myapplication.ml

import android.util.Log
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.FaceVectorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThresholdExperiment {

    private const val TAG = "FaceThresholdExperiment"

    suspend fun run(db: AppDb) = withContext(Dispatchers.Default) {

        val vectors = db.vectorDao().allVectors()
        if (vectors.size < 2) {
            Log.d(TAG, "Not enough embeddings for experiment.")
            return@withContext
        }

        val samePersonScores = mutableListOf<Float>()
        val differentPersonScores = mutableListOf<Float>()

        for (i in vectors.indices) {
            for (j in i + 1 until vectors.size) {

                val v1 = EmbeddingCodec.fromByteArray(vectors[i].embedding)
                val v2 = EmbeddingCodec.fromByteArray(vectors[j].embedding)

                val sim = FaceMatcher.cosineSimilarity(v1, v2)

                if (vectors[i].personId == vectors[j].personId) {
                    samePersonScores.add(sim)
                } else {
                    differentPersonScores.add(sim)
                }
            }
        }

        logStats("SAME_PERSON", samePersonScores)
        logStats("DIFFERENT_PERSON", differentPersonScores)
    }

    private fun logStats(label: String, values: List<Float>) {
        if (values.isEmpty()) {
            Log.d(TAG, "$label: no samples")
            return
        }

        val min = values.minOrNull()
        val max = values.maxOrNull()
        val mean = values.average()

        Log.d(TAG, "$label -> count=${values.size}")
        Log.d(TAG, "$label -> min=$min")
        Log.d(TAG, "$label -> max=$max")
        Log.d(TAG, "$label -> mean=$mean")
    }
}
