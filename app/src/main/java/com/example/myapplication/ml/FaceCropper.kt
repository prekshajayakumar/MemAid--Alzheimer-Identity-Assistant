package com.example.myapplication.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object FaceCropper {

    private val detector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        FaceDetection.getClient(opts)
    }

    suspend fun detectLargestFace(bitmap: Bitmap): Rect? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val biggest = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    cont.resume(biggest?.boundingBox)
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    fun crop(bitmap: Bitmap, rect: Rect): Bitmap? {
        val left = rect.left.coerceAtLeast(0)
        val top = rect.top.coerceAtLeast(0)
        val right = rect.right.coerceAtMost(bitmap.width)
        val bottom = rect.bottom.coerceAtMost(bitmap.height)

        val w = (right - left)
        val h = (bottom - top)
        if (w <= 0 || h <= 0) return null

        return Bitmap.createBitmap(bitmap, left, top, w, h)
    }
}
