package com.example.campusbuddy.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.AppPrimaryButton
import com.example.campusbuddy.ui.components.AppTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EmailVerificationScreen(
    repository: CampusBuddyRepository,
    onNavigateToProfileSetup: () -> Unit
) {
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isOtpError by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(60) }
    var canResend by remember { mutableStateOf(false) }
    val emailScope = rememberCoroutineScope()

    LaunchedEffect(countdown) {
        if (countdown > 0 && !canResend) {
            delay(1000)
            countdown--
            if (countdown == 0) canResend = true
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
                text = "We sent a verification code to your college email",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            AppTextField(
                value = otp,
                onValueChange = { if (it.length <= 6) { otp = it; isOtpError = false; errorMessage = null } },
                label = "6-digit OTP Code",
                placeholder = "000000",
                isError = isOtpError,
                errorMessage = errorMessage,
                keyboardType = KeyboardType.Number,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            )

            Spacer(modifier = Modifier.height(24.dp))

            AppPrimaryButton(
                text = "Verify",
                onClick = {
                    if (otp.length == 6) {
                        isLoading = true
                        emailScope.launch {
                            val user = repository.getCurrentFirebaseUser()
                            if (user != null) {
                            repository.updateUserProfile(user.uid, mapOf(
                                "isVerifiedStudent" to true,
                                "status" to com.example.campusbuddy.data.enums.UserStatus.VERIFIED.name
                            ))
                            }
                            isLoading = false
                            onNavigateToProfileSetup()
                        }
                    } else {
                        isOtpError = true
                        errorMessage = "Please enter a 6-digit code"
                    }
                },
                isLoading = isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = {
                    if (canResend) {
                        countdown = 60
                        canResend = false
                    }
                },
                enabled = canResend
            ) {
                Text(
                    text = if (canResend) "Resend Code" else "Resend in ${countdown}s",
                    color = if (canResend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
