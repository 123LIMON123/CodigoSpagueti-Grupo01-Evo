package com.upn3.aplicacinparadetectarenfermedadesvisuales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.model.DiseaseDetail
import com.upn3.aplicacinparadetectarenfermedadesvisuales.domain.model.OcularDisease

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseInfoScreen(navController: NavController) {
    val diseases = rememberDiseaseData()

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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(diseases) { diseaseDetail ->
                DiseaseCard(diseaseDetail)
            }
        }
    }
}

@Composable
fun DiseaseCard(detail: DiseaseDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = detail.disease.displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = detail.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Síntomas comunes:",
                style = MaterialTheme.typography.titleSmall
            )
            detail.symptoms.forEach { symptom ->
                Text(
                    text = "• $symptom",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recomendación:",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = detail.recommendations,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
fun rememberDiseaseData(): List<DiseaseDetail> {
    return listOf(
        DiseaseDetail(
            OcularDisease.NORMAL,
            "Ojo sin anomalías detectables en el fondo de ojo.",
            listOf("Visión clara", "Sin dolor", "Sin manchas"),
            "Mantenga chequeos regulares anualmente."
        ),
        DiseaseDetail(
            OcularDisease.DIABETES,
            "La retinopatía diabética es una complicación de la diabetes que afecta los ojos.",
            listOf("Visión borrosa", "Manchas oscuras (flotadores)", "Dificultad para ver colores"),
            "Controle sus niveles de azúcar en sangre y visite a un oftalmólogo."
        ),
        DiseaseDetail(
            OcularDisease.GLAUCOMA,
            "Grupo de condiciones oculares que dañan el nervio óptico, a menudo por presión alta.",
            listOf("Pérdida de visión periférica", "Visión de túnel", "Dolor ocular severo (en casos agudos)"),
            "El tratamiento temprano puede prevenir la pérdida total de la visión."
        ),
        DiseaseDetail(
            OcularDisease.CATARACT,
            "Opacidad del cristalino del ojo que normalmente es transparente.",
            listOf("Visión nublada", "Sensibilidad a la luz", "Dificultad para ver de noche"),
            "La cirugía de cataratas es un procedimiento común y seguro."
        ),
        DiseaseDetail(
            OcularDisease.AMD,
            "Degeneración Macular Relacionada con la Edad afecta la visión central detallada.",
            listOf("Distorsión de líneas rectas", "Mancha oscura en el centro del campo visual"),
            "Consuma una dieta rica en antioxidantes y use protección UV."
        ),
        DiseaseDetail(
            OcularDisease.HYPERTENSION,
            "La hipertensión puede dañar los vasos sanguíneos de la retina.",
            listOf("Generalmente asintomática al inicio", "Dolores de cabeza", "Visión doble"),
            "Controle su presión arterial regularmente."
        ),
        DiseaseDetail(
            OcularDisease.MYOPIA,
            "La miopía patológica puede causar cambios degenerativos en el fondo de ojo.",
            listOf("Visión lejana borrosa", "Fatiga visual", "Entrecerrar los ojos"),
            "Use corrección óptica y realice exámenes de fondo de ojo periódicos."
        )
    )
}
