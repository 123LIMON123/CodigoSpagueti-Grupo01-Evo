package com.upn3.aplicacinparadetectarenfermedadesvisuales.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.model.ClassificationResult
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.repository.OcularClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraViewModel(
    private val classifier: OcularClassifier
) : ViewModel() {

    private val _results = MutableStateFlow<List<ClassificationResult>>(emptyList())
    val results: StateFlow<List<ClassificationResult>> = _results.asStateFlow()

    fun onImageAnalyzed(bitmap: Bitmap, rotation: Int) {
        _results.value = classifier.classify(bitmap, rotation)
    }
}
