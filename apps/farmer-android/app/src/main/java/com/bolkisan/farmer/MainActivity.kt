package com.bolkisan.farmer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.bolkisan.farmer.domain.OfflineInferenceEngine
import com.bolkisan.farmer.domain.OfflineModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.bolkisan.farmer.presentation.auth.LanguageSelectScreen
import com.bolkisan.farmer.presentation.auth.LoginScreen
import com.bolkisan.farmer.presentation.disease.DiseaseCameraScreen
import com.bolkisan.farmer.presentation.home.HomeScreen
import com.bolkisan.farmer.presentation.navigation.Screen
import com.bolkisan.farmer.presentation.offline.OfflineLibraryScreen
import com.bolkisan.farmer.presentation.tickets.OfficerTicketsScreen
import com.bolkisan.farmer.presentation.voice.VoiceAssistantScreen
import com.bolkisan.farmer.theme.BolKisanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as BolKisanApp
        val repository = app.repository
        val offlineEngine = OfflineInferenceEngine.getInstance(this)

        // Asynchronously provision bundled offline SLM on first launch and initialize engine
        lifecycleScope.launch(Dispatchers.Default) {
            OfflineModelManager.provisionBundledModelOnFirstLaunch(this@MainActivity)
            if (OfflineModelManager.isGenuineModelInstalled(this@MainActivity)) {
                offlineEngine.initializeEngine()
            }
        }

        setContent {
            BolKisanTheme {
                val navController = rememberNavController()
                val currentLang by repository.languageFlow.collectAsState()

                // Direct entry to Voice Assistant Home
                val startDestination = Screen.Home.route

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable(Screen.LanguageSelect.route) {
                        LanguageSelectScreen(
                            currentLanguage = currentLang,
                            onLanguageSelected = { lang ->
                                repository.currentLanguage = lang
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.LanguageSelect.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Login.route) {
                        LoginScreen(
                            repository = repository,
                            onLoginSuccess = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.LanguageSelect.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Home.route) {
                        HomeScreen(
                            repository = repository,
                            onNavigateToVoice = {
                                navController.navigate(Screen.VoiceAssistant.route)
                            },
                            onNavigateToDiseaseCamera = {
                                navController.navigate(Screen.DiseaseCamera.route)
                            },
                            onNavigateToOfflineLibrary = {
                                navController.navigate(Screen.OfflineLibrary.route)
                            },
                            onNavigateToOfficerTickets = {
                                navController.navigate(Screen.OfficerTickets.route)
                            },
                            onSwitchLanguage = {
                                navController.navigate(Screen.LanguageSelect.route)
                            }
                        )
                    }

                    composable(Screen.VoiceAssistant.route) {
                        VoiceAssistantScreen(
                            repository = repository,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.DiseaseCamera.route) {
                        DiseaseCameraScreen(
                            repository = repository,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.OfflineLibrary.route) {
                        OfflineLibraryScreen(
                            repository = repository,
                            inferenceEngine = offlineEngine,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.OfficerTickets.route) {
                        OfficerTicketsScreen(
                            repository = repository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
