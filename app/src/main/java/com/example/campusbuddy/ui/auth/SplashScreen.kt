package com.example.campusbuddy.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    repository: CampusBuddyRepository,
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToProfileSetup: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }

    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(500))
        delay(300)

        val currentUser = repository.getCurrentFirebaseUser()
        if (currentUser == null) {
            onNavigateToLogin()
            return@LaunchedEffect
        }

        val profileResult = repository.getUserProfile(currentUser.uid)
        profileResult.onSuccess { profile ->
            if (profile.status == com.example.campusbuddy.data.enums.UserStatus.BANNED) {
                onNavigateToLogin()
            } else if (profile.fullName.isNotEmpty() && profile.department.isNotEmpty()) {
                onNavigateToHome()
            } else {
                onNavigateToProfileSetup()
            }
        }.onFailure {
            onNavigateToLogin()
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alphaAnim.value)
        ) {
            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CampusBuddy",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Find Your Study Partner",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            if (isLoading) {
                Spacer(modifier = Modifier.height(48.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
