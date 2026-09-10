package com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.usecase

import android.graphics.Bitmap
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.model.ClassificationResult
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.repository.OcularClassifier

class ClassifyOcularDiseaseUseCase(
    private val classifier: OcularClassifier
) {
    operator fun invoke(bitmap: Bitmap, rotation: Int): List<ClassificationResult> {
        return classifier.classify(bitmap, rotation)
    }
}
