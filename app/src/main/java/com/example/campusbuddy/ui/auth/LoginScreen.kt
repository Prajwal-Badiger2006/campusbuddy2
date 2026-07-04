package com.example.campusbuddy.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repository: CampusBuddyRepository,
    onNavigateToSignup: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val scope = rememberCoroutineScope()

    fun validateAndLogin() {
        var hasError = false
        if (email.isBlank()) { emailError = true; hasError = true }
        if (password.isBlank()) { passwordError = true; hasError = true }
        if (hasError) return

        isLoading = true
        errorMessage = null

        scope.launch {
            val result = repository.loginWithEmail(email, password)
            result.onSuccess {
                isLoading = false
                onNavigateToHome()
            }.onFailure { e ->
                isLoading = false
                errorMessage = e.message ?: "Login failed. Please try again."
                emailError = true
                passwordError = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sign in to continue with CampusBuddy",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            AppTextField(
                value = email,
                onValueChange = { email = it; emailError = false; errorMessage = null },
                label = "College Email",
                placeholder = "you@college.edu",
                isError = emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                leadingIcon = Icons.Filled.Email
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppPasswordField(
                value = password,
                onValueChange = { password = it; passwordError = false; errorMessage = null },
                label = "Password",
                isError = passwordError
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AppPrimaryButton(
                text = "Sign In",
                onClick = ::validateAndLogin,
                isLoading = isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppTextButton(
                text = "Forgot Password?",
                onClick = onNavigateToForgotPassword
            )

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Don't have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onNavigateToSignup) {
                    Text("Sign Up")
                }
            }
        }
    }
}
