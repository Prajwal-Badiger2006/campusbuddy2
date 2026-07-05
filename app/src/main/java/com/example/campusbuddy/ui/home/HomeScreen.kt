package com.example.campusbuddy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.enums.RequestStatus
import com.example.campusbuddy.data.local.UserPreferences
import com.example.campusbuddy.data.models.UserProfile
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import com.example.campusbuddy.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: CampusBuddyRepository,
    onNavigateToCreateRequest: () -> Unit,
    onNavigateToRequestDetails: (Long) -> Unit,
    onNavigateToPartnerProfile: (String) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMatches: () -> Unit,
    onNavigateToScanId: () -> Unit = {}
) {
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var recommendedPartners by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var openRequests by remember { mutableStateOf<List<com.example.campusbuddy.data.models.PartnerRequest>>(emptyList()) }
    var unreadCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var showVerificationPopup by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show success snackbar if returning from completed ID scan
    LaunchedEffect(Unit) {
        if (userPreferences.shouldShowVerificationSuccess()) {
            snackbarHostState.showSnackbar("Verification Successful! Badge Unlocked.")
            userPreferences.clearVerificationSuccess()
        }
    }

    LaunchedEffect(Unit) {
        val currentUser = repository.getCurrentFirebaseUser() ?: return@LaunchedEffect
        repository.getUserProfile(currentUser.uid).onSuccess { profile ->
            userProfile = profile
            // Show verification popup if user is not verified and hasn't seen the popup before
            if (!profile.isVerifiedStudent && !userPreferences.hasSeenVerificationPopup()) {
                showVerificationPopup = true
            }
            repository.getRecommendedPartners(profile).onSuccess { partners ->
                recommendedPartners = partners
            }
            repository.getPartnerRequests(profile.collegeName).onSuccess { requests ->
                openRequests = requests
            }
            unreadCount = repository.getUnreadNotificationCount(currentUser.uid)
        }
        isLoading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("CampusBuddy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                actions = {
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge { Text("$unreadCount") }
                            }
                        }
                    ) {
                        IconButton(onClick = onNavigateToNotifications) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreateRequest,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Request")
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                repeat(4) { SkeletonCard(modifier = Modifier.padding(bottom = 12.dp)) }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Greeting
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hi, ${userProfile?.fullName?.split(" ")?.first() ?: "Student"}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (userProfile?.isVerifiedStudent == true) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Verified",
                                modifier = Modifier.size(24.dp),
                                tint = VerifiedBadge
                            )
                        }
                    }
                }

                // Streak & Reliability badges
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val profile = userProfile
                        if (profile != null && profile.currentStreak > 0) {
                            StreakCard(streak = profile.currentStreak, modifier = Modifier.weight(1f))
                        }
                        if (profile != null && profile.reliabilityScore > 0) {
                            ReliabilityCard(score = profile.reliabilityScore, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Recommended Partners
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recommended Partners",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = onNavigateToMatches) {
                            Text("See All")
                        }
                    }
                }

                item {
                    if (recommendedPartners.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Complete your profile to get partner recommendations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recommendedPartners) { partner ->
                                PartnerCard(
                                    partner = partner,
                                    onClick = { onNavigateToPartnerProfile(partner.id) }
                                )
                            }
                        }
                    }
                }

                // Open Requests
                item {
                    Text(
                        text = "Open Requests",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (openRequests.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.TaskAlt,
                            title = "No Open Requests",
                            subtitle = "No open requests found. Create one!"
                        )
                    }
                } else {
                    items(openRequests.take(10)) { request ->
                        RequestCard(
                            title = request.title,
                            type = request.type,
                            subjectOrTopic = request.subjectOrTopic,
                            creatorName = "Student",
                            department = userProfile?.department ?: "",
                            neededCount = request.neededCount,
                            acceptedCount = request.acceptedCount,
                            postedTime = formatTimeAgo(request.createdAt),
                            onClick = { onNavigateToRequestDetails(request.id) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // Verification Badge Popup — shown on first home screen visit when unverified
    if (showVerificationPopup) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissing by clicking outside */ },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = null,
                        tint = VerifiedBadge,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Get Your Verified Student Badge! 🛡️",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Verify your student status using your College ID to build trust and connect with more study partners.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            userPreferences.markVerificationPopupSeen()
                            showVerificationPopup = false
                            onNavigateToScanId()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Proceed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = {
                            userPreferences.markVerificationPopupSeen()
                            showVerificationPopup = false
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Do It Later", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {},
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun PartnerCard(
    partner: UserProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UserAvatar(photoUrl = partner.profilePhotoUrl, name = partner.fullName, size = 64)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = partner.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (partner.isVerifiedStudent) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = "Verified",
                        modifier = Modifier.size(16.dp),
                        tint = VerifiedBadge
                    )
                }
            }
            Text(text = "${partner.department} · ${partner.year}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            if (partner.interests.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    partner.interests.take(3).forEach { interest ->
                        Surface(shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                            Text(interest, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            if (partner.reliabilityScore > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("${partner.reliabilityScore.toInt()}% Match", style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> "${diff / 86400000}d ago"
    }
}
