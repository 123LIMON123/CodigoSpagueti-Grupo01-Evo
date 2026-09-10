package com.upn3.aplicacinparadetectarenfermedadesvisuales.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.upn3.aplicacinparadetectarenfermedadesvisuales.ui.screens.*

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(navController)
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController)
        }
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        composable(Screen.Camera.route) {
            CameraScreen(navController)
        }
        composable("analysis_result/{diseaseName}/{confidence}") { backStackEntry ->
            val diseaseName = backStackEntry.arguments?.getString("diseaseName") ?: ""
            val confidence = backStackEntry.arguments?.getString("confidence")?.toFloatOrNull() ?: 0f
            AnalysisResultScreen(navController, diseaseName, confidence)
        }
        composable(Screen.DiseaseInfo.route) {
            DiseaseInfoScreen(navController)
        }
        composable(Screen.History.route) {
            HistoryScreen(navController)
        }
        composable(Screen.Profile.route) {
            ProfileScreen(navController)
        }
    }
}
