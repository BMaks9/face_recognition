package com.example.face_recognition

import android.Manifest
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*

private const val KEYCODE_BIXBY = 282

class MainActivity : ComponentActivity() {

    // Состояние разрешения камеры
    private var cameraPermissionGranted by mutableStateOf(false)

    // Состояние переключения отображения имён (реальные / "Name")
    private val showDefaultName = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Запрос разрешения камеры
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            cameraPermissionGranted = granted
        }

        requestPermissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {
            Surface(color = MaterialTheme.colors.background) {
                if (cameraPermissionGranted) {
                    // Передаем состояние и функцию изменения состояния в CameraPreview
                    CameraPreview(
                        onShowDefaultNameChanged = {
                            // Если внутри CameraPreview понадобится сообщить об изменениях
                            showDefaultName.value = it
                        },
                        showDefaultName = showDefaultName.value
                    )
                } else {
                    // Можно отобразить сообщение о том, что нет разрешения
                }
            }
        }
    }

    // Переопределяем onKeyDown, чтобы отлавливать нажатия кнопок
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KEYCODE_BIXBY -> {
                // Переключаем состояние показа имён
                showDefaultName.value = !showDefaultName.value
                return true // обработано
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
