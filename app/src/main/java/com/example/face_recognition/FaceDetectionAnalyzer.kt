package com.example.face_recognition

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.nio.ByteBuffer

@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalGetImage::class)
class FaceDetectionAnalyzer(
    private val context: Context,
    private val onFacesDetected: (faces: List<Face>, embeddings: List<FloatArray>) -> Unit
) : ImageAnalysis.Analyzer {

    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(detectorOptions)
    private val embedder = FaceEmbedder(context)

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotation)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    val bitmap = imageProxy.toBitmap() // 👈 Конвертация imageProxy в Bitmap
                    val embeddings = mutableListOf<FloatArray>()

                    for (face in faces) {
                        val faceBitmap = cropFace(bitmap, face.boundingBox)
                        val embedding = embedder.getEmbedding(faceBitmap)
                        embeddings.add(embedding)
                    }

                    onFacesDetected(faces, embeddings)
                    imageProxy.close()
                }
                .addOnFailureListener { e ->
                    Log.e("FaceDetectionAnalyzer", "Ошибка при детекции лиц", e)
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun cropFace(bitmap: Bitmap, rect: Rect): Bitmap {
        val safeRect = Rect(
            rect.left.coerceIn(0, bitmap.width),
            rect.top.coerceIn(0, bitmap.height),
            rect.right.coerceIn(0, bitmap.width),
            rect.bottom.coerceIn(0, bitmap.height)
        )
        return Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
    }
}
