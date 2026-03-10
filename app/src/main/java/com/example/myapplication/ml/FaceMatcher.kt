package com.example.myapplication.ml

object FaceMatcher {

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        var dot = 0f
        for (i in 0 until n) {
            dot += a[i] * b[i]
        }
        return dot
    }

    fun averageTopK(scores: List<Float>, k: Int): Float {
        if (scores.isEmpty()) return Float.NEGATIVE_INFINITY
        val top = scores.sortedDescending().take(k)
        return top.average().toFloat()
    }
}