package com.example.campusbuddy.ui.matches

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.models.Match
import com.example.campusbuddy.data.models.UserProfile
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import com.example.campusbuddy.ui.theme.VerifiedBadge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun MatchesScreen(
    repository: CampusBuddyRepository,
    onBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit,
    onNavigateToPartnerProfile: (String) -> Unit
) {
    var partners by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    var conversations by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    val currentUser = repository.getCurrentFirebaseUser()
    val currentUserId = currentUser?.uid ?: ""

    // Real-time matches via Flow
    val allMatches by repository.getMatchesFlow(currentUserId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Deduplicate matches
    val matches = remember(allMatches) {
        val seenPairs = mutableSetOf<Pair<String, Long>>()
        allMatches.filter { match ->
            val partnerId = if (match.user1Id == currentUserId) match.user2Id else match.user1Id
            val key = partnerId to match.requestId
            if (key in seenPairs) false else {
                seenPairs.add(key)
                true
            }
        }
    }

    // Load partner profiles and conversation mappings when matches change in real-time
    LaunchedEffect(matches) {
        val user = currentUser ?: return@LaunchedEffect
        matches.forEach { match ->
            val partnerId = if (match.user1Id == user.uid) match.user2Id else match.user1Id
            repository.getUserProfile(partnerId).onSuccess { profile ->
                partners = partners + (partnerId to profile)
            }
        }
        repository.getUserConversations(user.uid).onSuccess { convList ->
            // Bug 1 fix: Use same dedup logic as ChatsScreen — take the FIRST conversation per partner
            val seenPartners = mutableSetOf<String>()
            convList.forEach { conv ->
                val partnerId = conv.memberIds.find { it != user.uid }
                if (partnerId != null && partnerId !in seenPartners) {
                    conversations = conversations + (partnerId to conv.id)
                    seenPartners.add(partnerId)
                }
            }
        }
        // Bug 2 fix: Small delay to prevent empty-state flicker while Firebase loads
        if (matches.isEmpty()) {
            delay(500)
        }
        isLoading = false
    }

    Scaffold(
        topBar = { AppTopBar(title = "Matches", onBack = onBack) }
    ) { innerPadding ->
        when {
            isLoading -> {
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                    repeat(3) { SkeletonCard(modifier = Modifier.padding(bottom = 12.dp)) }
                }
            }
            matches.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.Favorite,
                    title = "No Matches Yet",
                    subtitle = "Accept a request to create your first match!",
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(matches) { match ->
                        val user = repository.getCurrentFirebaseUser()
                        val partnerId = if (match.user1Id == user?.uid) match.user2Id else match.user1Id
                        val partner = partnerId?.let { partners[it] }
                        val convId = partnerId?.let { conversations[it] }

                        partner?.let {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToPartnerProfile(partnerId!!) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(photoUrl = it.profilePhotoUrl, name = it.fullName, size = 56)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(it.fullName, style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold)
                                            if (it.isVerifiedStudent) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Filled.Verified,
                                                    contentDescription = "Verified",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = VerifiedBadge
                                                )
                                            }
                                        }
                                        Text("${it.department} · ${it.year}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Request #${match.requestId}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline)
                                    }
                                    convId?.let { id ->
                                        Button(
                                            onClick = { onNavigateToChat(id) },
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Chat")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
