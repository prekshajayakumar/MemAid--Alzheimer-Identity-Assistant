package com.example.myapplication.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder

object EmbeddingCodec {

    fun toByteArray(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4)
        buffer.order(ByteOrder.nativeOrder())
        for (v in vector) {
            buffer.putFloat(v)
        }
        return buffer.array()
    }

    fun fromByteArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.nativeOrder())

        val floats = FloatArray(bytes.size / 4)
        for (i in floats.indices) {
            floats[i] = buffer.getFloat()
        }
        return floats
    }
}
