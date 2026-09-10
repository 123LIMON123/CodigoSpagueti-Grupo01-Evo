package com.upn3.aplicacinparadetectarenfermedadesvisuales.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.upn3.aplicacinparadetectarenfermedadesvisuales.data.repository.TfLiteOcularClassifier
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.model.ClassificationResult
import com.upn3.aplicacinparadetectarenfermedadesvisuales.ui.navigation.Screen
import com.upn3.aplicacinparadetectarenfermedadesvisuales.ui.viewmodel.CameraViewModel
import java.util.concurrent.Executors

@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        val classifier = remember { TfLiteOcularClassifier(context) }
        val viewModel: CameraViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CameraViewModel(classifier) as T
                }
            }
        )
        val results by viewModel.results.collectAsState()
        
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(
                onImageAnalyzed = { bitmap, rotation ->
                    viewModel.onImageAnalyzed(bitmap, rotation)
                }
            )
            
            // Overlay de resultados
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                if (results.isEmpty()) {
                    Text(
                        text = "Analizando... (Asegúrese de tener el modelo en assets)",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    results.take(3).forEach { result ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = result.disease.displayName, color = Color.White)
                            Text(text = "${(result.confidence * 100).toInt()}%", color = Color.White)
                        }
                    }
                    
                    Button(
                        onClick = {
                            val bestResult = results.first()
                            navController.navigate(
                                Screen.AnalysisResult.createRoute(
                                    bestResult.disease.displayName,
                                    bestResult.confidence
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Ver Detalle")
                    }
                }
                
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Volver")
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Se requiere permiso de cámara para continuar")
        }
    }
}

@Composable
fun CameraPreview(
    onImageAnalyzed: (Bitmap, Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    
    // Control de tiempo para no saturar el análisis
    var lastAnalysisTime by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
    
    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor) { imageProxy ->
                            val currentTime = System.currentTimeMillis()
                            // Analizar máximo una vez por segundo para evitar lentitud
                            if (currentTime - lastAnalysisTime >= 1000) {
                                try {
                                    val bitmap = imageProxy.toBitmap()
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    onImageAnalyzed(bitmap, rotation)
                                    lastAnalysisTime = currentTime
                                } catch (e: Exception) {
                                    Log.e("CameraPreview", "Error procesando imagen: ${e.message}")
                                }
                            }
                            imageProxy.close()
                        }
                    }
                
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error al iniciar cámara: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
