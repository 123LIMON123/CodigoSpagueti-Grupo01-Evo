package com.upn3.aplicacinparadetectarenfermedadesvisuales.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.Surface
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.model.ClassificationResult
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.model.OcularDisease
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.repository.OcularClassifier
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

class TfLiteOcularClassifier(
    private val context: Context,
    private val threshold: Float = 0.5f,
    private val maxResults: Int = 3
) : OcularClassifier {

    private var classifier: ImageClassifier? = null
    private var isInitializationAttempted = false

    private val imageProcessor by lazy {
        ImageProcessor.Builder().build()
    }

    private fun setupClassifier() {
        if (isInitializationAttempted) return
        isInitializationAttempted = true

        val baseOptions = BaseOptions.builder()
            .setNumThreads(2)
            .build()

        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(baseOptions)
            .setMaxResults(maxResults)
            .setScoreThreshold(threshold)
            .build()

        try {
            // Assumes the model file is in the assets folder
            classifier = ImageClassifier.createFromFileAndOptions(
                context,
                "ocular_disease_model.tflite",
                options
            )
            Log.d("TfLiteClassifier", "Modelo cargado exitosamente")
        } catch (e: Exception) {
            Log.e("TfLiteClassifier", "Error al inicializar TFLite: ${e.message}. Verifique que el archivo .tflite esté en assets.")
            classifier = null
        }
    }

    override fun classify(bitmap: Bitmap, rotation: Int): List<ClassificationResult> {
        if (classifier == null && !isInitializationAttempted) {
            setupClassifier()
        }

        val currentClassifier = classifier ?: return emptyList()

        return try {
            val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))

            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setOrientation(getOrientationFromRotation(rotation))
                .build()

            val results = currentClassifier.classify(tensorImage, imageProcessingOptions)

            results.flatMap { classification ->
                classification.categories.map { category ->
                    ClassificationResult(
                        disease = mapLabelToDisease(category.label),
                        confidence = category.score
                    )
                }
            }.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            Log.e("TfLiteClassifier", "Error durante la clasificación: ${e.message}")
            emptyList()
        }
    }

    private fun mapLabelToDisease(label: String): OcularDisease {
        return when (label.uppercase()) {
            "N", "NORMAL" -> OcularDisease.NORMAL
            "D", "DIABETES" -> OcularDisease.DIABETES
            "G", "GLAUCOMA" -> OcularDisease.GLAUCOMA
            "C", "CATARACT" -> OcularDisease.CATARACT
            "A", "AMD" -> OcularDisease.AMD
            "H", "HYPERTENSION" -> OcularDisease.HYPERTENSION
            "M", "MYOPIA" -> OcularDisease.MYOPIA
            else -> OcularDisease.OTHER
        }
    }

    private fun getOrientationFromRotation(rotation: Int): ImageProcessingOptions.Orientation {
        return when (rotation) {
            Surface.ROTATION_270 -> ImageProcessingOptions.Orientation.BOTTOM_RIGHT
            Surface.ROTATION_180 -> ImageProcessingOptions.Orientation.RIGHT_BOTTOM
            Surface.ROTATION_90 -> ImageProcessingOptions.Orientation.TOP_LEFT
            else -> ImageProcessingOptions.Orientation.RIGHT_TOP
        }
    }
}
