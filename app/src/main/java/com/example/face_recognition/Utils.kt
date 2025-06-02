// Utils.kt или в любом удобном месте

import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.face.Face
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

fun mapRectFromImageToPreview(
    faceRect: Rect,
    previewView: PreviewView,
    imageWidth: Int,
    imageHeight: Int,
    isFrontCamera: Boolean
): ComposeRect {
    val matrix = Matrix()

    val previewWidth = previewView.width.toFloat()
    val previewHeight = previewView.height.toFloat()

    // Если PreviewView ещё не измерен — возвращаем прямоугольник без изменений
    if (previewWidth == 0f || previewHeight == 0f) {
        return ComposeRect(
            offset = Offset(faceRect.left.toFloat(), faceRect.top.toFloat()),
            size = Size(faceRect.width().toFloat(), faceRect.height().toFloat())
        )
    }

    // Отражаем по горизонтали для фронтальной камеры (если надо)
    if (isFrontCamera) {
        matrix.postScale(-1f, 1f, imageWidth / 2f, imageHeight / 2f)
    }

    // Масштабируем из размеров изображения в размеры PreviewView
    val scaleX = previewWidth / imageWidth.toFloat()
    val scaleY = previewHeight / imageHeight.toFloat()
    matrix.postScale(scaleX, scaleY)

    val mappedRectF = android.graphics.RectF(faceRect)
    matrix.mapRect(mappedRectF)

    return ComposeRect(
        offset = Offset(mappedRectF.left, mappedRectF.top),
        size = Size(mappedRectF.width(), mappedRectF.height())
    )
}


fun mapRectFromImageToPreview(
    faceRect: Rect,
    previewViewSize: android.util.Size,
    imageSize: android.util.Size,
    isFrontCamera: Boolean
): ComposeRect {

    val previewWidth = previewViewSize.width.toFloat()
    val previewHeight = previewViewSize.height.toFloat()
    val imageWidth = imageSize.width.toFloat()
    val imageHeight = imageSize.height.toFloat()

    if (previewWidth == 0f || previewHeight == 0f) {
        return ComposeRect(
            offset = Offset(faceRect.left.toFloat(), faceRect.top.toFloat()),
            size = Size(faceRect.width().toFloat(), faceRect.height().toFloat())
        )
    }

    // Вычисляем scale и offset для FILL_CENTER (центрируем и масштабируем с сохранением пропорций)
    val scale = maxOf(previewWidth / imageWidth, previewHeight / imageHeight)

    val scaledWidth = imageWidth * scale
    val scaledHeight = imageHeight * scale

    val dx = (previewWidth - scaledWidth) / 2f
    val dy = (previewHeight - scaledHeight) / 2f

    // Преобразуем координаты лица из пространства камеры в пространство previewView
    val left = faceRect.left * scale + dx
    val top = faceRect.top * scale + dy
    val right = faceRect.right * scale + dx
    val bottom = faceRect.bottom * scale + dy

    // Зеркально отражаем по горизонтали для фронтальной камеры
    return if (isFrontCamera) {
        ComposeRect(
            offset = Offset(previewWidth - right, top),
            size = Size(right - left, bottom - top)
        )
    } else {
        ComposeRect(
            offset = Offset(left, top),
            size = Size(right - left, bottom - top)
        )
    }
}
