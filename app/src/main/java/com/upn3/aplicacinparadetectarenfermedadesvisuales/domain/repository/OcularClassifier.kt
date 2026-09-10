package com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.repository

import android.graphics.Bitmap
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.model.ClassificationResult

interface OcularClassifier {
    fun classify(bitmap: Bitmap, rotation: Int): List<ClassificationResult>
}
