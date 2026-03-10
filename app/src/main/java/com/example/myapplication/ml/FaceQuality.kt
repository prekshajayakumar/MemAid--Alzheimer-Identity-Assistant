package com.example.myapplication.ml

import android.graphics.Bitmap

data class FaceQualityResult(
    val accepted: Boolean,
    val brightness: Float,
    val contrast: Float,
    val sharpness: Float
)

object FaceQuality {

    fun evaluate(bitmap: Bitmap): FaceQualityResult {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 64 || h < 64) {
            return FaceQualityResult(
                accepted = false,
                brightness = 0f,
                contrast = 0f,
                sharpness = 0f
            )
        }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = FloatArray(w * h)

        var sum = 0f
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            gray[i] = y
            sum += y
        }

        val mean = sum / gray.size

        var varianceSum = 0f
        for (v in gray) {
            val d = v - mean
            varianceSum += d * d
        }
        val contrast = varianceSum / gray.size

        var sharpnessSum = 0f
        var count = 0

        for (y in 0 until h - 1) {
            for (x in 0 until w - 1) {
                val idx = y * w + x
                val gx = kotlin.math.abs(gray[idx] - gray[idx + 1])
                val gy = kotlin.math.abs(gray[idx] - gray[idx + w])
                sharpnessSum += gx + gy
                count++
            }
        }

        val sharpness = if (count > 0) sharpnessSum / count else 0f
        val brightness = mean

        val accepted =
            brightness in 50f..210f &&
                    contrast >= 350f &&
                    sharpness >= 12f

        return FaceQualityResult(
            accepted = accepted,
            brightness = brightness,
            contrast = contrast,
            sharpness = sharpness
        )
    }
}