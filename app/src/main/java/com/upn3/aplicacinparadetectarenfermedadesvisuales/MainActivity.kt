package com.upn3.aplicacinparadetectarenfermedadesvisuales

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.upn3.aplicacinparadetectarenfermedadesvisuales.ui.theme.AplicaciónParaDetectarEnfermedadesVisualesTheme
import java.util.concurrent.Executors

// ==========================================================================================
// TODO EL ESTADO DE LA APP VIVE AQUI. Mas facil acceder desde cualquier pantalla asi.
// ==========================================================================================
var classifierGlobal: org.tensorflow.lite.Interpreter? = null
var yaSeIntentoCargarModelo = false
var usuarioLogueado = false
var nombreUsuario = "Usuario de Prueba"
var emailUsuario = "usuario@ejemplo.com"
var ultimaEnfermedadDetectada = ""
var ultimaConfianzaDetectada = 0f
val historialDeAnalisis = mutableListOf<Pair<String, Float>>()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AplicaciónParaDetectarEnfermedadesVisualesTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavHost(navController)
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "login") {
        composable("login") { PantallaLogin(navController) }
        composable("register") { PantallaRegistro(navController) }
        composable("home") { PantallaHome(navController) }
        composable("camera") { PantallaCamara(navController) }
        composable("analysis_result/{diseaseName}/{confidence}") { backStackEntry ->
            val diseaseName = backStackEntry.arguments?.getString("diseaseName") ?: ""
            val confidence = backStackEntry.arguments?.getString("confidence")?.toFloatOrNull() ?: 0f
            PantallaResultadoAnalisis(navController, diseaseName, confidence)
        }
        composable("disease_info") { PantallaInfoEnfermedades(navController) }
        composable("history") { PantallaHistorial(navController) }
        composable("profile") { PantallaPerfil(navController) }
    }
}

// ==========================================================================================
// LOGIN / REGISTRO
// ==========================================================================================
@Composable
fun PantallaLogin(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Login Screen", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            usuarioLogueado = true
            navController.navigate("home")
        }) {
            Text(text = "Login")
        }
        TextButton(onClick = { navController.navigate("register") }) {
            Text(text = "Don't have an account? Register")
        }
    }
}

@Composable
fun PantallaRegistro(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Register Screen", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            usuarioLogueado = true
            navController.popBackStack()
        }) {
            Text(text = "Back to Login")
        }
    }
}

// ==========================================================================================
// HOME
// ==========================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaHome(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Eye Disease Detector") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Welcome to Eye Disease Detector", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { navController.navigate("camera") }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "New Analysis")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { navController.navigate("history") }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "History")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { navController.navigate("disease_info") }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Disease Information")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { navController.navigate("profile") }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Profile")
            }
        }
    }
}

// ==========================================================================================
// CAMARA + CLASIFICACION (todo mezclado aqui, la UI llama directo al TFLite)
// ==========================================================================================
@Composable
fun PantallaCamara(navController: NavController) {
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

    var resultados by remember { mutableStateOf(listOf<Pair<String, Float>>()) }
    var imagenSubida by remember { mutableStateOf<Bitmap?>(null) }

    // selector de galeria para subir una foto de un ojo en vez de usar la camara en vivo
    val selectorGaleria = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmapCargado = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmapCargado != null) {
                    imagenSubida = bitmapCargado
                    resultados = clasificarImagen(context, bitmapCargado, 0)
                }
            } catch (e: Exception) {
                Log.e("PantallaCamara", "Error al cargar la foto: ${e.message}")
            }
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imagenSubida != null) {
                Image(
                    bitmap = imagenSubida!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                CameraPreview(
                    onImageAnalyzed = { bitmap, rotation ->
                        resultados = clasificarImagen(context, bitmap, rotation)
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                if (resultados.isEmpty()) {
                    Text(
                        text = "Analizando... (Asegúrese de tener el modelo en assets)",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    resultados.take(3).forEach { resultado ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = resultado.first, color = Color.White)
                            Text(text = "${(resultado.second * 100).toInt()}%", color = Color.White)
                        }
                    }

                    Button(
                        onClick = {
                            val mejorResultado = resultados.first()
                            ultimaEnfermedadDetectada = mejorResultado.first
                            ultimaConfianzaDetectada = mejorResultado.second
                            historialDeAnalisis.add(mejorResultado)
                            navController.navigate("analysis_result/${mejorResultado.first}/${mejorResultado.second}")
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Ver Detalle")
                    }
                }

                Button(
                    onClick = { selectorGaleria.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(if (imagenSubida == null) "Subir foto de un ojo" else "Subir otra foto")
                }

                if (imagenSubida != null) {
                    OutlinedButton(
                        onClick = {
                            imagenSubida = null
                            resultados = emptyList()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Volver a la cámara en vivo")
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Se requiere permiso de cámara para continuar")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { selectorGaleria.launch("image/*") }) {
                    Text("O subir una foto de un ojo")
                }

                if (imagenSubida != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Image(
                        bitmap = imagenSubida!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.height(200.dp),
                        contentScale = ContentScale.Fit
                    )
                    resultados.take(3).forEach { resultado ->
                        Text(text = "${resultado.first}: ${(resultado.second * 100).toInt()}%")
                    }
                    if (resultados.isNotEmpty()) {
                        Button(onClick = {
                            val mejorResultado = resultados.first()
                            ultimaEnfermedadDetectada = mejorResultado.first
                            ultimaConfianzaDetectada = mejorResultado.second
                            historialDeAnalisis.add(mejorResultado)
                            navController.navigate("analysis_result/${mejorResultado.first}/${mejorResultado.second}")
                        }) {
                            Text("Ver Detalle")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(onImageAnalyzed: (Bitmap, Int) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    var lastAnalysisTime by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
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
                    CameraSelector.DEFAULT_FRONT_CAMERA,
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

// el modelo se entreno con MobileNetV2 a 160x160 y estas 8 clases, en este orden exacto
// (ver ml/train.py, LABEL_COLS) - si cambia el orden alla hay que cambiarlo aqui tambien
val ORDEN_CLASES_MODELO = listOf("N", "D", "G", "C", "A", "H", "M", "O")
const val TAMANO_ENTRADA_MODELO = 160

// funcion suelta que hace de "repositorio", "usecase" y "mapper" a la vez porque para que separar
fun clasificarImagen(context: Context, bitmap: Bitmap, rotation: Int): List<Pair<String, Float>> {
    if (classifierGlobal == null && !yaSeIntentoCargarModelo) {
        yaSeIntentoCargarModelo = true
        try {
            val descriptor: AssetFileDescriptor = context.assets.openFd("ocular_disease_model.tflite")
            val inputStream = java.io.FileInputStream(descriptor.fileDescriptor)
            val modeloMapeado = inputStream.channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength
            )
            classifierGlobal = org.tensorflow.lite.Interpreter(modeloMapeado)
            Log.d("TfLiteClassifier", "Modelo cargado exitosamente")
        } catch (e: Exception) {
            Log.e("TfLiteClassifier", "Error al inicializar TFLite: ${e.message}")
            classifierGlobal = null
        }
    }

    val clasificadorActual = classifierGlobal ?: return emptyList()

    return try {
        // corregimos la rotacion que viene de la camara antes de redimensionar
        val matrizRotacion = Matrix()
        matrizRotacion.postRotate(rotation.toFloat())
        val bitmapRotado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrizRotacion, true)
        val bitmapRedimensionado = Bitmap.createScaledBitmap(
            bitmapRotado, TAMANO_ENTRADA_MODELO, TAMANO_ENTRADA_MODELO, true
        )

        // ojo: el modelo ya trae el preprocess_input (mobilenet_v2) HORNEADO adentro del grafo,
        // asi que aqui se le pasan los pixeles crudos [0,255] tal cual, sin normalizar a mano
        // (normalizar aca tambien seria normalizar dos veces y arruina la clasificacion)
        val bufferEntrada = java.nio.ByteBuffer.allocateDirect(4 * TAMANO_ENTRADA_MODELO * TAMANO_ENTRADA_MODELO * 3)
        bufferEntrada.order(java.nio.ByteOrder.nativeOrder())
        val pixeles = IntArray(TAMANO_ENTRADA_MODELO * TAMANO_ENTRADA_MODELO)
        bitmapRedimensionado.getPixels(pixeles, 0, TAMANO_ENTRADA_MODELO, 0, 0, TAMANO_ENTRADA_MODELO, TAMANO_ENTRADA_MODELO)
        for (pixel in pixeles) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            bufferEntrada.putFloat(r.toFloat())
            bufferEntrada.putFloat(g.toFloat())
            bufferEntrada.putFloat(b.toFloat())
        }

        val bufferSalida = Array(1) { FloatArray(ORDEN_CLASES_MODELO.size) }
        clasificadorActual.run(bufferEntrada, bufferSalida)

        val listaFinal = mutableListOf<Pair<String, Float>>()
        for (i in ORDEN_CLASES_MODELO.indices) {
            val nombreEnfermedad = when (ORDEN_CLASES_MODELO[i]) {
                "N" -> "Normal"
                "D" -> "Diabetes"
                "G" -> "Glaucoma"
                "C" -> "Cataract"
                "A" -> "Age-related Macular Degeneration"
                "H" -> "Hypertension"
                "M" -> "Myopia"
                else -> "Other"
            }
            listaFinal.add(Pair(nombreEnfermedad, bufferSalida[0][i]))
        }
        listaFinal.sortByDescending { it.second }
        listaFinal
    } catch (e: Exception) {
        Log.e("TfLiteClassifier", "Error durante la clasificación: ${e.message}")
        emptyList()
    }
}

// ==========================================================================================
// RESULTADO DEL ANALISIS
// ==========================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaResultadoAnalisis(navController: NavController, diseaseName: String, confidence: Float) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resultado del Análisis") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Detección Probable:", style = MaterialTheme.typography.titleLarge)
            Text(
                text = diseaseName,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { confidence },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Text(text = "Confianza: ${(confidence * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(48.dp))

            Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Realizar nuevo análisis")
            }

            OutlinedButton(
                onClick = { navController.navigate("disease_info") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Más información sobre esta condición")
            }
        }
    }
}

// ==========================================================================================
// INFO DE ENFERMEDADES (la lista de enfermedades esta copiada y pegada aqui de nuevo,
// no se reutiliza nada del clasificador)
// ==========================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInfoEnfermedades(navController: NavController) {
    val enfermedades = listOf(
        listOf("Normal", "Ojo sin anomalías detectables en el fondo de ojo.", "Visión clara,Sin dolor,Sin manchas", "Mantenga chequeos regulares anualmente."),
        listOf("Diabetes", "La retinopatía diabética es una complicación de la diabetes que afecta los ojos.", "Visión borrosa,Manchas oscuras (flotadores),Dificultad para ver colores", "Controle sus niveles de azúcar en sangre y visite a un oftalmólogo."),
        listOf("Glaucoma", "Grupo de condiciones oculares que dañan el nervio óptico, a menudo por presión alta.", "Pérdida de visión periférica,Visión de túnel,Dolor ocular severo (en casos agudos)", "El tratamiento temprano puede prevenir la pérdida total de la visión."),
        listOf("Cataract", "Opacidad del cristalino del ojo que normalmente es transparente.", "Visión nublada,Sensibilidad a la luz,Dificultad para ver de noche", "La cirugía de cataratas es un procedimiento común y seguro."),
        listOf("Age-related Macular Degeneration", "Degeneración Macular Relacionada con la Edad afecta la visión central detallada.", "Distorsión de líneas rectas,Mancha oscura en el centro del campo visual", "Consuma una dieta rica en antioxidantes y use protección UV."),
        listOf("Hypertension", "La hipertensión puede dañar los vasos sanguíneos de la retina.", "Generalmente asintomática al inicio,Dolores de cabeza,Visión doble", "Controle su presión arterial regularmente."),
        listOf("Myopia", "La miopía patológica puede causar cambios degenerativos en el fondo de ojo.", "Visión lejana borrosa,Fatiga visual,Entrecerrar los ojos", "Use corrección óptica y realice exámenes de fondo de ojo periódicos.")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Información de Enfermedades") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(enfermedades) { datos ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = datos[0], style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = datos[1], style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Síntomas comunes:", style = MaterialTheme.typography.titleSmall)
                        datos[2].split(",").forEach { sintoma ->
                            Text(text = "• $sintoma", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Recomendación:", style = MaterialTheme.typography.titleSmall)
                        Text(text = datos[3], style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                    }
                }
            }
        }
    }
}

// ==========================================================================================
// HISTORIAL
// ==========================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaHistorial(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Análisis") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (historialDeAnalisis.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = "Aún no tienes análisis guardados.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                items(historialDeAnalisis) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = item.first)
                        Text(text = "${(item.second * 100).toInt()}%")
                    }
                }
            }
        }
    }
}

// ==========================================================================================
// PERFIL
// ==========================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPerfil(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = nombreUsuario, style = MaterialTheme.typography.headlineMedium)
            Text(text = emailUsuario, style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    usuarioLogueado = false
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Cerrar Sesión")
            }
        }
    }
}
