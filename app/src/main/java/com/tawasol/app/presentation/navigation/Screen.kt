package com.tawasol.app.presentation.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Home : Screen("home")
    data object ChatDetail : Screen("chat_detail/{conversationId}") {
        fun createRoute(conversationId: String) = "chat_detail/$conversationId"
    }
    data object UserSearch : Screen("user_search")
    data object UserProfile : Screen("user_profile/{userId}") {
        fun createRoute(userId: String) = "user_profile/$userId"
    }
    data object EditProfile : Screen("edit_profile")
    data object PrivacySettings : Screen("privacy_settings")
    data object Settings : Screen("settings")
    data object SafetyNumber : Screen("safety_number/{peerUserId}") {
        fun createRoute(peerUserId: String) = "safety_number/$peerUserId"
    }
}

