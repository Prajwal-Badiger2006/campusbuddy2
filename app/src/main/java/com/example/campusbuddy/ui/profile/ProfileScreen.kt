package com.example.campusbuddy.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.enums.UserStatus
import com.example.campusbuddy.data.models.UserProfile
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import com.example.campusbuddy.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: CampusBuddyRepository,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToMatches: () -> Unit,
    onNavigateToNotifications: () -> Unit
) {
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val user = repository.getCurrentFirebaseUser() ?: return@LaunchedEffect
        repository.getUserProfile(user.uid).onSuccess { profile ->
            userProfile = profile
        }
        isLoading = false
    }

    // Reload when coming back from edit
    LaunchedEffect(Unit) {
        val user = repository.getCurrentFirebaseUser()
        if (user != null) {
            repository.getUserProfile(user.uid).onSuccess {
                userProfile = it
            }
        }
    }

    if (showLogoutDialog) {
        ConfirmationDialog(
            title = "Logout",
            message = "Are you sure you want to log out?",
            confirmLabel = "Logout",
            onConfirm = {
                repository.logout()
                showLogoutDialog = false
                onNavigateToLogin()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                repeat(3) { SkeletonCard(modifier = Modifier.padding(bottom = 12.dp)) }
            }
        } else if (userProfile == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Failed to load profile")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Profile header
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    UserAvatar(photoUrl = userProfile!!.profilePhotoUrl,
                        name = userProfile!!.fullName, size = 96)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(userProfile!!.fullName, style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold)
                        if (userProfile!!.isVerifiedStudent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Filled.Verified, contentDescription = "Verified",
                                modifier = Modifier.size(24.dp), tint = VerifiedBadge)
                        }
                    }
                    Text("${userProfile!!.collegeName}", style = MaterialTheme.typography.bodyMedium)
                    Text("${userProfile!!.department} · ${userProfile!!.year}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Streak & Reliability
                if (userProfile!!.currentStreak > 0) {
                    StreakCard(streak = userProfile!!.currentStreak)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                ReliabilityCard(score = userProfile!!.reliabilityScore)
                Spacer(modifier = Modifier.height(16.dp))

                // Interests
                if (userProfile!!.interests.isNotEmpty()) {
                    Text("Interests", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        userProfile!!.interests.forEach {
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                                Text(it, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Skills
                if (userProfile!!.skills.isNotEmpty()) {
                    Text("Skills", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        userProfile!!.skills.forEach {
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)) {
                                Text(it, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Actions
                AppPrimaryButton(text = "Edit Profile", onClick = onNavigateToEditProfile)
                Spacer(modifier = Modifier.height(12.dp))

                AppSecondaryButton(text = "View Matches", onClick = onNavigateToMatches)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout")
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
