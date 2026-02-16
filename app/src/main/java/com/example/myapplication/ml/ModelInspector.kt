package com.example.myapplication.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

object ModelInspector {
    fun log(context: Context, filename: String) {
        val model = FileUtil.loadMappedFile(context, filename)
        val tflite = Interpreter(model)

        val inTensor = tflite.getInputTensor(0)
        val outTensor = tflite.getOutputTensor(0)

        Log.d("ModelInspector", "IN  shape=${inTensor.shape().contentToString()} type=${inTensor.dataType()}")
        Log.d("ModelInspector", "OUT shape=${outTensor.shape().contentToString()} type=${outTensor.dataType()}")
    }
}
