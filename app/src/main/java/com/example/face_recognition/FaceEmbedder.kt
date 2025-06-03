package com.example.face_recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class FaceEmbedder(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = FileUtil.loadMappedFile(context, "mobilefacenet.tflite")
        interpreter = Interpreter(model)
    }

    fun getEmbedding(bitmap: Bitmap): FloatArray {
        val input = preprocess(bitmap)
        val embedding = Array(1) { FloatArray(192) }
        Log.d("FaceEmbedder", "Embedding получен: ${embedding[0].take(5)}")
        interpreter.run(input, embedding)


        return l2Normalize(embedding[0])
    }

    private fun preprocess(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val resized = Bitmap.createScaledBitmap(bitmap, 112, 112, true)

        val input = Array(1) { Array(112) { Array(112) { FloatArray(3) } } }

        for (y in 0 until 112) {
            for (x in 0 until 112) {
                val pixel = resized.getPixel(x, y)
                input[0][y][x][0] = ((pixel shr 16 and 0xFF) - 127.5f) / 128.0f
                input[0][y][x][1] = ((pixel shr 8 and 0xFF) - 127.5f) / 128.0f
                input[0][y][x][2] = ((pixel and 0xFF) - 127.5f) / 128.0f
            }
        }

        return input
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        val norm = sqrt(embedding.map { it * it }.sum())
        return embedding.map { it / norm }.toFloatArray()
    }
}
