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

// Email Verification
@Serializable
object EmailVerificationRoute

// Source from which the ID scanner was launched, determines return navigation
@Serializable
enum class ScanSource {
    ONBOARDING,  // From the signup/email-verification flow — return to Onboarding → Home
    HOME,        // From the Home screen pop-up — return to Home
    PROFILE      // From the Profile banner — return to Profile
}

// OCR Student ID Scanner — source indicates where to navigate back after completion
@Serializable
data class ScanIdRoute(val source: ScanSource = ScanSource.ONBOARDING)
