package com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.model

data class DiseaseDetail(
    val disease: OcularDisease,
    val description: String,
    val symptoms: List<String>,
    val recommendations: String
)
