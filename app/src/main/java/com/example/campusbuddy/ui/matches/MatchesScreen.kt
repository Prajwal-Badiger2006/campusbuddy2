package com.example.campusbuddy.ui.matches

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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

@Composable
fun MatchesScreen(
    repository: CampusBuddyRepository,
    onBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit,
    onNavigateToPartnerProfile: (String) -> Unit
) {
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var partners by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    var conversations by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val user = repository.getCurrentFirebaseUser() ?: return@LaunchedEffect
        repository.getUserMatches(user.uid).onSuccess { matchList ->
            // Deduplicate: keep only the first match per unique (partnerId, requestId) pair
            val seenPairs = mutableSetOf<Pair<String, Long>>()
            matches = matchList.filter { match ->
                val partnerId = if (match.user1Id == user.uid) match.user2Id else match.user1Id
                val key = partnerId to match.requestId
                if (key in seenPairs) false else {
                    seenPairs.add(key)
                    true
                }
            }
            matches.forEach { match ->
                val partnerId = if (match.user1Id == user.uid) match.user2Id else match.user1Id
                repository.getUserProfile(partnerId).onSuccess { profile ->
                    partners = partners + (partnerId to profile)
                }
            }
            repository.getUserConversations(user.uid).onSuccess { convList ->
                convList.forEach { conv ->
                    val partnerId = conv.memberIds.find { it != user.uid }
                    if (partnerId != null) {
                        conversations = conversations + (partnerId to conv.id)
                    }
                }
            }
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
                                        Text(it.fullName, style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold)
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
