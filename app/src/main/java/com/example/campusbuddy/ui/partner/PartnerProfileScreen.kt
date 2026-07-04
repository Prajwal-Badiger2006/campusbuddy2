package com.example.campusbuddy.ui.partner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.models.UserProfile
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import com.example.campusbuddy.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerProfileScreen(
    partnerUserId: String,
    repository: CampusBuddyRepository,
    onBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit,
    onNavigateToReport: (String, String) -> Unit
) {
    var partner by remember { mutableStateOf<UserProfile?>(null) }
    var currentUser by remember { mutableStateOf<UserProfile?>(null) }
    var isAlreadyMatched by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var sharedConversationId by remember { mutableStateOf<Long?>(null) }
    var showReportMenu by remember { mutableStateOf(false) }

    LaunchedEffect(partnerUserId) {
        repository.getUserProfile(partnerUserId).onSuccess { partner = it }
        val user = repository.getCurrentFirebaseUser()
        if (user != null) {
            repository.getUserProfile(user.uid).onSuccess { currentUser = it }
            repository.getUserMatches(user.uid).onSuccess { matches ->
                val match = matches.find { it.user1Id == partnerUserId || it.user2Id == partnerUserId }
                if (match != null) {
                    isAlreadyMatched = true
                    repository.getUserConversations(user.uid).onSuccess { conversations ->
                        val conv = conversations.find {
                            it.memberIds.contains(partnerUserId) && it.memberIds.contains(user.uid)
                        }
                        sharedConversationId = conv?.id
                    }
                }
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(partner?.fullName ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showReportMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showReportMenu,
                            onDismissRequest = { showReportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Report User") },
                                onClick = {
                                    showReportMenu = false
                                    onNavigateToReport(partnerUserId, partner?.fullName ?: "")
                                },
                                leadingIcon = { Icon(Icons.Filled.Report, contentDescription = null) }
                            )
                        }
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
        } else if (partner == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("User not found")
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
                    UserAvatar(photoUrl = partner!!.profilePhotoUrl, name = partner!!.fullName, size = 96)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(partner!!.fullName, style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold)
                        if (partner!!.isVerifiedStudent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Filled.Verified, contentDescription = "Verified",
                                modifier = Modifier.size(24.dp), tint = VerifiedBadge)
                        }
                    }
                    Text("${partner!!.collegeName}", style = MaterialTheme.typography.bodyMedium)
                    Text("${partner!!.department} · ${partner!!.year}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusBadge(partner!!.status.name, color = when(partner!!.status) {
                        com.example.campusbuddy.data.enums.UserStatus.VERIFIED -> Secondary
                        com.example.campusbuddy.data.enums.UserStatus.ALUMNI -> Tertiary
                        else -> Error
                    })
                }

                Spacer(modifier = Modifier.height(16.dp))

                partner!!.bio?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Streak & Reliability
                if (partner!!.currentStreak > 0) {
                    StreakCard(streak = partner!!.currentStreak)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (partner!!.reliabilityScore > 0) {
                    ReliabilityCard(score = partner!!.reliabilityScore)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Interests
                if (partner!!.interests.isNotEmpty()) {
                    Text("Interests", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    ChipSelector(options = emptyList(), selectedOptions = partner!!.interests, onToggle = {})
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        partner!!.interests.forEach {
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
                if (partner!!.skills.isNotEmpty()) {
                    Text("Skills", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        partner!!.skills.forEach {
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)) {
                                Text(it, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Compatibility Card
                currentUser?.let { current ->
                    val sharedInterests = current.interests.intersect(partner!!.interests.toSet()).toList()
                    val sharedAvail = current.availability.intersect(partner!!.availability.toSet()).toList()
                    val sharedGoals = current.goals.intersect(partner!!.goals.toSet()).toList()
                    val matchPercent = minOf(
                        (sharedInterests.size * 5 + sharedAvail.size * 5 + sharedGoals.size * 5), 100
                    )

                    if (sharedInterests.isNotEmpty() || sharedAvail.isNotEmpty() || sharedGoals.isNotEmpty()) {
                        CompatibilityCard(
                            matchPercentage = matchPercent,
                            sharedInterests = sharedInterests,
                            sharedAvailability = sharedAvail,
                            sharedGoals = sharedGoals
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Action
                if (isAlreadyMatched && sharedConversationId != null) {
                    AppPrimaryButton(
                        text = "Open Chat",
                        onClick = { onNavigateToChat(sharedConversationId!!) }
                    )
                } else if (isAlreadyMatched) {
                    AppPrimaryButton(text = "Matched ✓", onClick = {}, enabled = false)
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = color.copy(alpha = 0.1f)) {
        Text(status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, color = color)
    }
}
