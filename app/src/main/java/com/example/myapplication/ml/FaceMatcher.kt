package com.example.myapplication.ml

object FaceMatcher {

    // Assumes embeddings are already L2-normalized
    // Cosine similarity reduces to dot product
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot
    }
}
