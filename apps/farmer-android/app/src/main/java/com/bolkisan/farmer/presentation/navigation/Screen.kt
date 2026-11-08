package com.bolkisan.farmer.presentation.navigation

sealed class Screen(val route: String) {
    object LanguageSelect : Screen("language_select")
    object Login : Screen("login")
    object Home : Screen("home")
    object VoiceAssistant : Screen("voice_assistant")
    object DiseaseCamera : Screen("disease_camera")
    object OfflineLibrary : Screen("offline_library")
    object OfficerTickets : Screen("officer_tickets")
}
