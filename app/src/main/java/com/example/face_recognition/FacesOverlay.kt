package com.example.face_recognition

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.face.Face

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.nativeCanvas
import mapRectFromImageToPreview

@Composable
fun FacesOverlay(
    faces: List<Face>,
    names: List<String>,  // добавляем список имен по индексам лиц
    previewViewSize: android.util.Size,
    imageSize: android.util.Size,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val scale = minOf(
            size.width / imageSize.width.toFloat(),
            size.height / imageSize.height.toFloat()
        )

        val offsetX = (size.width - imageSize.width * scale) / 2f
        val offsetY = (size.height - imageSize.height * scale) / 2f

        for ((index, face) in faces.withIndex()) {
            val box = face.boundingBox

            val left = box.left * scale + offsetX
            val top = box.top * scale + offsetY
            val right = box.right * scale + offsetX
            val bottom = box.bottom * scale + offsetY

            val rectLeft = if (isFrontCamera) size.width - right else left
            val rectRight = if (isFrontCamera) size.width - left else right
            val verticalCorrection = -80f  // экспериментально

            // Рисуем прямоугольник
            drawRect(
                color = Color.Red,
                topLeft = Offset(rectLeft, top + verticalCorrection),
                size = Size(rectRight - rectLeft, bottom - top),
                style = Stroke(width = 4f)
            )

            // Рисуем имя рядом с лицом
            val text = names.getOrNull(index) ?: "Unknown"

            // Получаем nativeCanvas
            val nativeCanvas = drawContext.canvas.nativeCanvas

            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 40f
                isAntiAlias = true
                setShadowLayer(5f, 0f, 0f, android.graphics.Color.BLACK)
            }

            nativeCanvas.drawText(
                text,
                rectLeft,
                top + verticalCorrection - 10f,
                paint
            )
        }
    }
}


