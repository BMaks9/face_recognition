package com.example.face_recognition

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {

    private var cameraPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Разрешение камеры
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            cameraPermissionGranted = granted
        }

        requestPermissionLauncher.launch(Manifest.permission.CAMERA)


            setContent {
                Surface(color = MaterialTheme.colors.background) {
                    if (cameraPermissionGranted) {
                        CameraPreview()
                    }

            }
            }
        }
    }
