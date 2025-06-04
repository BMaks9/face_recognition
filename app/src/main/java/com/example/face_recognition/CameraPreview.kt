package com.example.face_recognition

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.face.Face
import java.util.concurrent.Executors

data class KnownPerson(
    val name: String,
    val embeddings: MutableList<FloatArray>
)

private const val SIMILARITY_THRESHOLD = 0.85f


@SuppressLint("RestrictedApi")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    showDefaultName: Boolean,
    onShowDefaultNameChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = rememberPreviewView(context)
    val previewViewSize = remember { mutableStateOf(Size(0, 0)) }
    val imageSize = Size(1280, 720)
    val isFrontCamera = true

    val knownPersons = remember { mutableStateListOf<KnownPerson>() }
    val faces = remember { mutableStateListOf<Face>() }
    val faceCache = remember { mutableStateMapOf<String, String>() }

    var lastEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var nameInput by remember { mutableStateOf("") }

    var isButtonPressed by remember { mutableStateOf(false) } // новое состояние удержания

    // Загрузка известных лиц из хранилища
    LaunchedEffect(Unit) {
        knownPersons.clear()
        knownPersons.addAll(loadKnownPersonsFromJson(context))

        startCamera(context, lifecycleOwner, previewView) { detectedFaces, detectedEmbeddings ->
            faces.clear()
            faces.addAll(detectedFaces)

            faceCache.clear()
            detectedFaces.zip(detectedEmbeddings).forEach { (face, embedding) ->
                faceCache[faceKey(face)] = findBestMatch(knownPersons, embedding) ?: "Незнакомец"
            }

            lastEmbedding = detectedEmbeddings.firstOrNull()
        }
    }

    val displayedNames = getDisplayedNames(faces, faceCache, showDefaultName, isButtonPressed)

    Box(modifier = Modifier.fillMaxSize()) {
        // Камера превью
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .matchParentSize()
                .onGloballyPositioned {
                    previewViewSize.value = Size(it.size.width, it.size.height)
                }
        )

        // Отображение рамок и имен
        FacesOverlay(
            faces = faces,
            names = displayedNames,
            previewViewSize = previewViewSize.value,
            imageSize = imageSize,
            isFrontCamera = isFrontCamera,
            modifier = Modifier.matchParentSize()
        )

        // 👉 Кнопка удержания — нижний левый угол
        Button(
            onClick = { /* пусто */ },
            modifier = Modifier
                .size(width = 80.dp, height = 80.dp)
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 10.dp)
                .alpha(0.0f)
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isButtonPressed = true
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isButtonPressed = false
                            true
                        }
                        else -> false
                    }
                }
        ) {
            Text("")
        }

        // 👉 Поле ввода и кнопка "Запомнить" — нижний центр
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Введите имя") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                handleEmbedding(nameInput, lastEmbedding, knownPersons, context)
            }) {
                Text("Запомнить лицо")
            }
        }
    }

}

@Composable
private fun rememberPreviewView(context: Context): PreviewView {
    return remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
}

private fun handleEmbedding(
    nameInput: String,
    embedding: FloatArray?,
    knownPersons: MutableList<KnownPerson>,
    context: Context
) {
    embedding?.let {
        val normalized = normalize(it)
        val name = nameInput.ifBlank { "Пользователь ${knownPersons.size + 1}" }

        val existingPerson = knownPersons.find { person -> person.name == name }
        if (existingPerson != null) {
            if (!isEmbeddingKnown(existingPerson.embeddings, normalized)) {
                existingPerson.embeddings.add(normalized)
                Log.d("FaceRecognition", "Добавлен ракурс к $name")
            }
        } else {
            knownPersons.add(KnownPerson(name, mutableListOf(normalized)))
            Log.d("FaceRecognition", "Новое лицо: $name")
        }

        saveKnownPersonsToJson(context, knownPersons)
    }
}

private fun getDisplayedNames(
    faces: List<Face>,
    faceCache: Map<String, String>,
    showDefaultName: Boolean,
    isButtonPressed: Boolean = false,
    pressedText: String = "Максим"
): List<String> {
    return if (isButtonPressed) {
        List(faces.size) { pressedText }
    } else {
        faces.map { face ->
            val key = faceKey(face)
            if (showDefaultName) "Максим" else faceCache[key] ?: ""
        }
    }
}

fun faceKey(face: Face): String {
    val box = face.boundingBox
    return "${box.centerX() / 10}_${box.centerY() / 10}_${box.width() / 10}_${box.height() / 10}"
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
            .setTargetResolution(Size(1280, 720))
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

// Здесь loadKnownPersonsFromJson и saveKnownPersonsToJson — твои функции загрузки/сохранения данных лиц, их нужно определить отдельно.



data class SerializableKnownPerson(
    val name: String,
    val embeddings: List<List<Float>>
)

fun KnownPerson.toSerializable(): SerializableKnownPerson {
    return SerializableKnownPerson(
        name,
        embeddings.map { it.toList() }
    )
}

fun SerializableKnownPerson.toModel(): KnownPerson {
    return KnownPerson(
        name,
        embeddings.map { it.toFloatArray() }.toMutableList() // <- добавлено toMutableList()
    )
}
