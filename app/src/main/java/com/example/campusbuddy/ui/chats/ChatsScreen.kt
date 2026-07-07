package com.example.campusbuddy.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.models.Conversation
import com.example.campusbuddy.data.models.UserProfile
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import com.example.campusbuddy.ui.theme.VerifiedBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    repository: CampusBuddyRepository,
    onNavigateToChat: (Long) -> Unit
) {
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var partners by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    var lastMessages by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val user = repository.getCurrentFirebaseUser() ?: return@LaunchedEffect
        repository.getUserConversations(user.uid).onSuccess { convs ->
            // Deduplicate: keep only the first conversation per unique partner
            val seenPartners = mutableSetOf<String>()
            conversations = convs.filter { conv ->
                // Always keep group conversations — only deduplicate 1-on-1 chats
                if (conv.isGroup) return@filter true
                val partnerId = conv.memberIds.find { it != user.uid }
                if (partnerId == null) {
                    true
                } else if (partnerId in seenPartners) {
                    false // Duplicate 1-on-1 conversation with same partner
                } else {
                    seenPartners.add(partnerId)
                    true
                }
            }
            conversations.forEach { conv ->
                val partnerId = conv.memberIds.find { it != user.uid }
                if (partnerId != null) {
                    repository.getUserProfile(partnerId).onSuccess {
                        partners = partners + (partnerId to it)
                    }
                }
                repository.getMessages(conv.id).onSuccess { msgs ->
                    if (msgs.isNotEmpty()) {
                        lastMessages = lastMessages + (conv.id to msgs.last().content)
                    }
                }
            }
        }
        isLoading = false
    }

    val filteredConversations = if (searchQuery.isBlank()) conversations
    else conversations.filter { conv ->
        val partnerId = conv.memberIds.find { it != repository.getCurrentFirebaseUser()?.uid }
        val partner = partnerId?.let { partners[it] }
        partner?.fullName?.contains(searchQuery, ignoreCase = true) ?: false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            AppSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search conversations...",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                isLoading -> {
                    Column(modifier = Modifier.padding(16.dp)) {
                        repeat(4) { SkeletonCard(modifier = Modifier.padding(bottom = 12.dp)) }
                    }
                }
                filteredConversations.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.ChatBubbleOutline,
                        title = "No Chats Yet",
                        subtitle = "Accept a partner to start chatting"
                    )
                }
                else -> {
                    LazyColumn {
                        items(filteredConversations) { conv ->
                            val currentUserId = repository.getCurrentFirebaseUser()?.uid
                            val partnerId = conv.memberIds.find { it != currentUserId }
                            val partner = partnerId?.let { partners[it] }
                            val lastMsg = lastMessages[conv.id]

                            ListItem(
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(partner?.fullName ?: "Chat", fontWeight = FontWeight.SemiBold)
                                        if (partner?.isVerifiedStudent == true) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Filled.Verified,
                                                contentDescription = "Verified",
                                                modifier = Modifier.size(16.dp),
                                                tint = VerifiedBadge
                                            )
                                        }
                                    }
                                },
                                supportingContent = {
                                    if (lastMsg != null) {
                                        Text(
                                            text = lastMsg.take(40) + if (lastMsg.length > 40) "..." else "",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingContent = {
                                    partner?.let {
                                        UserAvatar(photoUrl = it.profilePhotoUrl, name = it.fullName, size = 48)
                                    }
                                },
                                modifier = Modifier.clickable { onNavigateToChat(conv.id) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}
