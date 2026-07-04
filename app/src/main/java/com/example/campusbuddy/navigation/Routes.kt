package com.example.campusbuddy.navigation

import kotlinx.serialization.Serializable

// Auth Layer
@Serializable
object SplashRoute

@Serializable
object LoginRoute

@Serializable
object SignupRoute

@Serializable
object ForgotPasswordRoute

// Setup Layer
@Serializable
object ProfileSetupRoute

@Serializable
object OnboardingRoute

// Main Layer (Bottom Navigation tabs)
@Serializable
object HomeRoute

@Serializable
object RequestsRoute

@Serializable
object ChatsRoute

@Serializable
object ProfileRoute

// Detail Layer (Stack-pushed)
@Serializable
object CreateRequestRoute

@Serializable
data class RequestDetailsRoute(val requestId: Long)

@Serializable
data class MyRequestDetailsRoute(val requestId: Long)

@Serializable
data class PartnerProfileRoute(val partnerUserId: String)

@Serializable
data class ChatRoute(val conversationId: Long)

@Serializable
object MatchesRoute

@Serializable
object NotificationsRoute

@Serializable
object EditProfileRoute

@Serializable
object SettingsRoute

@Serializable
data class ReportUserRoute(val targetUserId: String, val targetUserName: String)
