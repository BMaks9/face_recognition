package com.example.face_recognition

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import com.google.mlkit.vision.face.Face

@SuppressLint("RestrictedApi")
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val faces = remember { mutableStateListOf<Face>() }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val previewViewSize = remember { mutableStateOf(android.util.Size(0, 0)) }
    val imageSize = android.util.Size(640, 480)
    val isFrontCamera = true

    LaunchedEffect(Unit) {
        startCamera(context, lifecycleOwner, previewView) { newFaces ->
            faces.clear()
            faces.addAll(newFaces)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned { layoutCoordinates ->
                val width = layoutCoordinates.size.width
                val height = layoutCoordinates.size.height
                previewViewSize.value = android.util.Size(width, height)
            }
        )
        FacesOverlay(
            faces = faces,
            previewViewSize = previewViewSize.value,
            imageSize = imageSize,
            isFrontCamera = isFrontCamera,
            modifier = Modifier.matchParentSize()
        )

    }
}




private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onFacesDetected: (List<Face>) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val analyzerExecutor = Executors.newSingleThreadExecutor()

        imageAnalyzer.setAnalyzer(analyzerExecutor, FaceDetectionAnalyzer { faces ->
            // Здесь у тебя список лиц в кадре, можешь логировать, рисовать или анализировать
            Log.d("FaceDetection", "Найдено лиц: ${faces.size}")
            onFacesDetected(faces)
            for (face in faces) {
                Log.d("FaceDetection", "Лицо с трекинг ID: ${face.trackingId}, bounding box: ${face.boundingBox}")
            }
        })

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (exc: Exception) {
            exc.printStackTrace()
        }

    }, ContextCompat.getMainExecutor(context))
}
