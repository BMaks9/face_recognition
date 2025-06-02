package com.example.face_recognition

import android.util.Log
import androidx.annotation.experimental.UseExperimental
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.ExperimentalGetImage

@UseExperimental(ExperimentalGetImage::class)
class CameraAnalyzer : ImageAnalysis.Analyzer {
    override fun analyze(imageProxy: ImageProxy) {
        val rotation = imageProxy.imageInfo.rotationDegrees
        val image = imageProxy.image

        if (image != null) {
            Log.d("CameraAnalyzer", "Кадр получен. Rotation: $rotation")
            // Здесь можно добавить ML Kit или TensorFlow Lite
        }

        imageProxy.close()
    }
}
