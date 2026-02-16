package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceEmbedder(context: Context) {

    private val interpreter: Interpreter

    // MobileFaceNet commonly: 112x112 RGB, output 192 floats
    private val inputSize = 112
    private val embeddingDim = 192

    init {
        // IMPORTANT: must match EXACT asset name.
        // Your screenshot shows: app/src/main/assets/mobilefacenet.tflite
        val model = FileUtil.loadMappedFile(context, "mobilefacenet.tflite")
        interpreter = Interpreter(model)
    }

    fun embed(faceBitmap: Bitmap): FloatArray {
        val input: ByteBuffer = bitmapToInputBuffer(faceBitmap, inputSize)

        // Output shape: [1, 192]
        val output = Array(1) { FloatArray(embeddingDim) }

        interpreter.run(input, output)
        return output[0]
    }

    private fun bitmapToInputBuffer(bitmap: Bitmap, inputSize: Int): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        // Input shape: [1, inputSize, inputSize, 3] float32
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            // Normalize to [-1, 1]
            // Most MobileFaceNet conversions use (x - 127.5) / 127.5
            inputBuffer.putFloat((r - 127.5f) / 127.5f)
            inputBuffer.putFloat((g - 127.5f) / 127.5f)
            inputBuffer.putFloat((b - 127.5f) / 127.5f)
        }

        inputBuffer.rewind()
        return inputBuffer
    }
}
