package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class FaceEmbedder(context: Context) {

    private val interpreter: Interpreter

    private val inputSize = 112
    private val embeddingDim = 192

    init {
        val model = FileUtil.loadMappedFile(context, "mobilefacenet.tflite")
        interpreter = Interpreter(model)
    }

    fun embed(faceBitmap: Bitmap): FloatArray {
        val input: ByteBuffer = bitmapToInputBuffer(faceBitmap, inputSize)
        val output = Array(1) { FloatArray(embeddingDim) }

        interpreter.run(input, output)

        return l2Normalize(output[0])
    }

    private fun bitmapToInputBuffer(bitmap: Bitmap, inputSize: Int): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            inputBuffer.putFloat((r - 127.5f) / 127.5f)
            inputBuffer.putFloat((g - 127.5f) / 127.5f)
            inputBuffer.putFloat((b - 127.5f) / 127.5f)
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0f
        for (v in vector) sum += v * v
        val norm = sqrt(sum)

        return if (norm > 0f) {
            vector.map { it / norm }.toFloatArray()
        } else {
            vector
        }
    }
}
