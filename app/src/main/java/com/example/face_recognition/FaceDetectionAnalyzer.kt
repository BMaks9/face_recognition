package com.example.face_recognition

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

@SuppressLint("UnsafeOptInUsageError") // чтобы не ругался на imageProxy.image
@OptIn(ExperimentalGetImage::class)
class FaceDetectionAnalyzer(
    private val onFacesDetected: (faces: List<Face>) -> Unit
) : ImageAnalysis.Analyzer {

    // Настройки детектора лиц — высокая точность и детекция масок тоже работает
    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // Быстрая обработка
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)       // Можно включить, если нужны точки лица
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE) // Можно включить улыбки и моргания
        .enableTracking() // Для идентификации лиц в разных кадрах
        .build()

    private val detector = FaceDetection.getClient(detectorOptions)

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    // Отправляем найденные лица в UI или логику
                    onFacesDetected(faces)
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
}
