package com.example.campusbuddy.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.AppPrimaryButton
import kotlinx.coroutines.delay

@Composable
fun EmailVerificationScreen(
    repository: CampusBuddyRepository,
    onNavigateToProfileSetup: () -> Unit
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isEmailVerified by remember { mutableStateOf(false) }
    var isEmailSent by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(60) }
    var canResend by remember { mutableStateOf(false) }
    var checkingVerification by remember { mutableStateOf(false) }
    var verificationAttempts by remember { mutableIntStateOf(0) }
    val maxVerificationAttempts = 60 // ~3 minutes of polling

    // Start countdown and send verification email
    LaunchedEffect(Unit) {
        val user = repository.getCurrentFirebaseUser() ?: return@LaunchedEffect
        if (!user.isEmailVerified) {
            user.sendEmailVerification()
                .addOnCompleteListener { task ->
                    isEmailSent = task.isSuccessful
                    if (!task.isSuccessful) {
                        errorMessage = task.exception?.message ?: "Failed to send verification email"
                    }
                }
        } else {
            isEmailVerified = true
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0 && !canResend) {
            delay(1000)
            countdown--
            if (countdown == 0) canResend = true
        }
    }

    // Periodically check if email is verified
    LaunchedEffect(isEmailSent) {
        if (isEmailSent) {
            delay(15000) // Wait 15 seconds before first check to give user time
            while (!isEmailVerified && verificationAttempts < maxVerificationAttempts) {
                checkingVerification = true
                val user = repository.getCurrentFirebaseUser()
                if (user != null) {
                    user.reload().addOnCompleteListener {
                        if (user.isEmailVerified) {
                            isEmailVerified = true
                            // Note: isVerifiedStudent is NOT set here.
                            // The verified student badge only activates on ID scan.
                        }
                        checkingVerification = false
                    }
                }
                verificationAttempts++
                if (!isEmailVerified) {
                    delay(3000) // Check every 3 seconds
                }
            }
            if (!isEmailVerified) {
                checkingVerification = false
                errorMessage = "Still waiting for verification. Tap resend to get a new email, or skip for now."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Verify Your Email",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We sent a verification email. Please check your inbox and click the verification link.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (isEmailVerified) {
                // Email verified — proceed to profile setup
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Email Verified!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        AppPrimaryButton(
                            text = "Continue to Profile Setup",
                            onClick = onNavigateToProfileSetup
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Set up your interests, skills, and preferences to find the best matches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (isEmailSent) {
                // Show status while waiting for verification
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (checkingVerification) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (checkingVerification) "Checking verification status..."
                                else "Verification email sent! Click the link in your email.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Resend button
                TextButton(
                    onClick = {
                        if (canResend) {
                            countdown = 60
                            canResend = false
                            errorMessage = null
                            val user = repository.getCurrentFirebaseUser()
                            user?.sendEmailVerification()
                                ?.addOnCompleteListener { task ->
                                    isEmailSent = task.isSuccessful
                                    if (!task.isSuccessful) {
                                        errorMessage = task.exception?.message ?: "Failed to resend"
                                    }
                                }
                        }
                    },
                    enabled = canResend
                ) {
                    Text(
                        text = if (canResend) "Resend Verification Email" else "Resend in ${countdown}s",
                        color = if (canResend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Skip to profile setup without email verification
                TextButton(
                    onClick = onNavigateToProfileSetup
                ) {
                    Text(
                        text = "Skip email verification for now",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onNavigateToProfileSetup
                ) {
                    Text(
                        text = "Continue without verification",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
