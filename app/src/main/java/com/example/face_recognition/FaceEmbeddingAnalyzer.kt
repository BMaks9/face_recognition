package com.example.face_recognition

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.example.face_recognition.FaceEmbedder // Замените на ваш реальный пакет
import androidx.camera.core.ImageAnalysis.Analyzer
import kotlinx.coroutines.flow.callbackFlow
import android.graphics.Matrix
import android.graphics.PointF
import com.google.mlkit.vision.face.FaceLandmark

class FaceEmbeddingAnalyzer(
    private val context: Context,
    private val onResultsReady: (faces: List<Face>, embeddings: List<FloatArray>) -> Unit

) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // <-- заменить
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .enableTracking()
            .build()
    )

    private val embedder = FaceEmbedder(context)

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                Log.d("FaceEmbeddingAnalyzer", "Найдено лиц: ${faces.size}")

                val embeddings = faces.mapNotNull { face ->
                    try {

                        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

                        if (leftEye != null && rightEye != null) {
                            val faceBitmapRaw = Bitmap.createBitmap(
                                bitmap,
                                face.boundingBox.left.coerceAtLeast(0),
                                face.boundingBox.top.coerceAtLeast(0),
                                face.boundingBox.width().coerceAtMost(bitmap.width - face.boundingBox.left),
                                face.boundingBox.height().coerceAtMost(bitmap.height - face.boundingBox.top)
                            )

                            val leftEyeRelative = PointF(
                                leftEye.x - face.boundingBox.left,
                                leftEye.y - face.boundingBox.top
                            )
                            val rightEyeRelative = PointF(
                                rightEye.x - face.boundingBox.left,
                                rightEye.y - face.boundingBox.top
                            )

                            val alignedFaceBitmap = alignFace(faceBitmapRaw, leftEyeRelative, rightEyeRelative)
                            embedder.getEmbedding(alignedFaceBitmap)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("Embedding", "Ошибка при извлечении лица", e)
                        null
                    }
                }

                onResultsReady(faces, embeddings)  // вот так передаём отдельно

                imageProxy.close()
            }
            .addOnFailureListener {
                Log.e("FaceEmbeddingAnalyzer", "Ошибка распознавания", it)
                imageProxy.close()
            }

    }
}


fun alignFace(
    bitmap: Bitmap,
    leftEye: PointF,
    rightEye: PointF,
    desiredFaceWidth: Int = 112,
    desiredFaceHeight: Int = 112,
    desiredLeftEyeX: Float = 0.35f,
    desiredLeftEyeY: Float = 0.35f
): Bitmap {
    val dx = rightEye.x - leftEye.x
    val dy = rightEye.y - leftEye.y
    val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()

    val desiredRightEyeX = 1.0f - desiredLeftEyeX

    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
    val desiredDist = (desiredRightEyeX - desiredLeftEyeX) * desiredFaceWidth
    val scale = desiredDist / dist

    val eyesCenterX = (leftEye.x + rightEye.x) / 2
    val eyesCenterY = (leftEye.y + rightEye.y) / 2

    val matrix = Matrix()

    // Перенос центра глаз к началу координат
    matrix.postTranslate(-eyesCenterX, -eyesCenterY)
    // Поворот для выравнивания глаз горизонтально
    matrix.postRotate(-angle)
    // Масштабирование к нужному размеру
    matrix.postScale(scale, scale)
    // Перемещение глаз в желаемое положение в выходном битмапе
    matrix.postTranslate(desiredFaceWidth * 0.5f, desiredFaceHeight * desiredLeftEyeY)

    val alignedBitmap = Bitmap.createBitmap(desiredFaceWidth, desiredFaceHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(alignedBitmap)
    canvas.drawBitmap(bitmap, matrix, null)

    return alignedBitmap
}
