package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class FaceEmbedder(context: Context) : Closeable {

    private val interpreter: Interpreter

    private val inputSize = 112
    private val embeddingDim = 192

    init {
        val model = FileUtil.loadMappedFile(context, "mobilefacenet.tflite")

        val opts = Interpreter.Options().apply {
            setNumThreads(2)
        }

        interpreter = Interpreter(model, opts)
    }

    fun embed(faceBitmap: Bitmap): FloatArray {
        val input: ByteBuffer = bitmapToInputBuffer(faceBitmap)
        val output = Array(1) { FloatArray(embeddingDim) }

        interpreter.run(input, output)

        l2NormalizeInPlace(output[0])
        return output[0]
    }

    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
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

    private fun l2NormalizeInPlace(v: FloatArray) {
        var sum = 0f
        for (x in v) sum += x * x

        val norm = sqrt(sum)
        if (norm <= 1e-12f) return

        val inv = 1f / norm
        for (i in v.indices) {
            v[i] *= inv
        }
    }

    override fun close() {
        interpreter.close()
    }
}
