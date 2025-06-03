package com.example.face_recognition

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import com.google.mlkit.vision.face.Face

data class KnownPerson(val name: String, val embeddings: List<FloatArray>)
private const val SIMILARITY_THRESHOLD = 0.7f


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

    val faceNames = remember { mutableStateListOf<String>() }

    val knownPersons = remember { mutableStateListOf<KnownPerson>() }
    var lastEmbedding by remember { mutableStateOf<List<Float>?>(null) }

    var nameInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        startCamera(context, lifecycleOwner, previewView) { detectedFaces, detectedEmbeddings ->
            // Обновляем состояние лиц в UI
            faces.clear()
            faces.addAll(detectedFaces)

            faceNames.clear()
            detectedEmbeddings.forEach { embedding ->
                val matchedName = findBestMatch(knownPersons, embedding)
                faceNames.add(matchedName ?: "Незнакомец")
            }

            // Обновляем последний эмбеддинг (например, первого лица)
            lastEmbedding = detectedEmbeddings.firstOrNull()?.toList()

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
            names = faceNames,
            previewViewSize = previewViewSize.value,
            imageSize = imageSize,
            isFrontCamera = isFrontCamera,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {

            OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Введите имя") }
        )
            Button(onClick = {
                lastEmbedding?.let { embedding ->
                    val embeddingArray = embedding.toFloatArray()
                    val normalized = normalize(embeddingArray)

                    // Если поле пустое, используем предыдущее имя или создаём автоматическое
                    val personName = if (nameInput.isNotBlank()) nameInput else "Пользователь ${knownPersons.size + 1}"

                    // Сохраняем имя, если было введено впервые
                    if (nameInput.isNotBlank()) {
                        nameInput = personName // запоминаем
                    }

                    val existingPerson = knownPersons.find { it.name == personName }
                    if (existingPerson != null) {
                        if (!isEmbeddingKnown(existingPerson.embeddings, normalized)) {
                            val updated = existingPerson.embeddings.toMutableList()
                            updated.add(normalized)
                            knownPersons.remove(existingPerson)
                            knownPersons.add(KnownPerson(existingPerson.name, updated))
                            Log.d("FaceRecognition", "Добавлен новый ракурс к $personName")
                        } else {
                            Log.d("FaceRecognition", "Эмбеддинг уже существует для $personName")
                        }
                    } else {
                        knownPersons.add(KnownPerson(personName, mutableListOf(normalized)))
                        Log.d("FaceRecognition", "Лицо запомнено как $personName")
                    }
                }
            }) {
                Text("Запомнить лицо")
            }

        }
}
}




private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onResults: (faces: List<Face>, embeddings: List<FloatArray>) -> Unit
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

        imageAnalyzer.setAnalyzer(
            analyzerExecutor,
            FaceEmbeddingAnalyzer(context) { faces, embeddings ->
                onResults(faces, embeddings)
            }
        )

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

fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
    val dot = vec1.zip(vec2) { a, b -> a * b }.sum()
    val normA = kotlin.math.sqrt(vec1.fold(0.0) { acc, f -> acc + f * f })
    val normB = kotlin.math.sqrt(vec2.fold(0.0) { acc, f -> acc + f * f })
    return (dot / (normA * normB + 1e-6)).toFloat()
}
fun normalize(vector: FloatArray): FloatArray {
    val norm = kotlin.math.sqrt(vector.fold(0.0) { acc, v -> acc + v * v }).toFloat()
    return if (norm == 0f) vector else vector.map { it / norm }.toFloatArray()
}

fun isEmbeddingKnown(
    knownEmbeddings: List<FloatArray>,
    newEmbedding: FloatArray,
    threshold: Float = SIMILARITY_THRESHOLD
): Boolean {
    val normalizedNew = normalize(newEmbedding)
    return knownEmbeddings.any { known ->
        val normalizedKnown = normalize(known)
        cosineSimilarity(normalizedKnown, normalizedNew) > threshold
    }
}

fun addEmbeddingIfNew(
    knownEmbeddings: MutableList<FloatArray>,
    newEmbedding: FloatArray,
    threshold: Float = 0.6f
) {
    if (!isEmbeddingKnown(knownEmbeddings, newEmbedding, threshold)) {
        knownEmbeddings.add(newEmbedding)
    }
}


fun findBestMatch(
    knownPersons: List<KnownPerson>,
    newEmbedding: FloatArray,
    threshold: Float = SIMILARITY_THRESHOLD
): String? {
    val normalizedNew = normalize(newEmbedding)
    var bestScore = -1f
    var bestMatch: KnownPerson? = null

    for (person in knownPersons) {
        for (embedding in person.embeddings) {
            val score = cosineSimilarity(normalize(embedding), normalizedNew)
            if (score > bestScore && score > threshold) {
                bestScore = score
                bestMatch = person
            }
        }
    }

    return bestMatch?.name
}

