package com.upn3.aplicacinparadetectarenfermedadesvisuales.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object AnalysisResult : Screen("analysis_result/{diseaseName}/{confidence}") {
        fun createRoute(diseaseName: String, confidence: Float) = "analysis_result/$diseaseName/$confidence"
    }
    object History : Screen("history")
    object DiseaseInfo : Screen("disease_info")
    object Profile : Screen("profile")
}
