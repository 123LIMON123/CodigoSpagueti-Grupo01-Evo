package com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.model

enum class OcularDisease(val displayName: String) {
    NORMAL("Normal"),
    DIABETES("Diabetes"),
    GLAUCOMA("Glaucoma"),
    CATARACT("Cataract"),
    AMD("Age-related Macular Degeneration"),
    HYPERTENSION("Hypertension"),
    MYOPIA("Myopia"),
    OTHER("Other")
}

data class ClassificationResult(
    val disease: OcularDisease,
    val confidence: Float
)
