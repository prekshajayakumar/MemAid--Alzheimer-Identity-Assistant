package com.example.myapplication.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max

data class DetectedFace(
    val boundingBox: Rect,
    val yaw: Float,
    val roll: Float
)

object FaceCropper {

    private val detector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(0.12f)
            .build()
        FaceDetection.getClient(opts)
    }

    suspend fun detectSingleUsableFace(bitmap: Bitmap): DetectedFace? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            detector.process(image)
                .addOnSuccessListener { faces ->
                    cont.resume(pickSingleUsableFace(bitmap, faces))
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    suspend fun detectLargestFace(bitmap: Bitmap): Rect? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val biggest = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    cont.resume(biggest?.boundingBox)
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    private fun pickSingleUsableFace(bitmap: Bitmap, faces: List<Face>): DetectedFace? {
        if (faces.size != 1) return null

        val face = faces.first()
        val box = face.boundingBox

        if (box.width() < 64 || box.height() < 64) return null
        if (face.headEulerAngleY !in -28f..28f) return null
        if (face.headEulerAngleZ !in -20f..20f) return null

        if (box.left < 0 || box.top < 0 || box.right > bitmap.width || box.bottom > bitmap.height) {
            return null
        }

        return DetectedFace(
            boundingBox = box,
            yaw = face.headEulerAngleY,
            roll = face.headEulerAngleZ
        )
    }

    fun cropSquare(bitmap: Bitmap, rect: Rect, paddingRatio: Float = 0.28f): Bitmap? {
        val cx = rect.centerX()
        val cy = rect.centerY()

        val side = (max(rect.width(), rect.height()) * (1f + paddingRatio)).toInt()

        var left = cx - side / 2
        var top = cy - side / 2
        var right = left + side
        var bottom = top + side

        if (left < 0) {
            right -= left
            left = 0
        }
        if (top < 0) {
            bottom -= top
            top = 0
        }
        if (right > bitmap.width) {
            val diff = right - bitmap.width
            left -= diff
            right = bitmap.width
        }
        if (bottom > bitmap.height) {
            val diff = bottom - bitmap.height
            top -= diff
            bottom = bitmap.height
        }

        left = left.coerceAtLeast(0)
        top = top.coerceAtLeast(0)
        right = right.coerceAtMost(bitmap.width)
        bottom = bottom.coerceAtMost(bitmap.height)

        val width = right - left
        val height = bottom - top
        val finalSide = minOf(width, height)

        if (finalSide < 40) return null

        return Bitmap.createBitmap(bitmap, left, top, finalSide, finalSide)
    }

    fun crop(bitmap: Bitmap, rect: Rect, paddingRatio: Float = 0.20f): Bitmap? {
        return cropSquare(bitmap, rect, paddingRatio)
    }
}