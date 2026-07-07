package com.example.campusbuddy.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import com.example.campusbuddy.ui.theme.VerifiedBadge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.models.Conversation
import com.example.campusbuddy.data.models.Message
import com.example.campusbuddy.data.models.UserProfile
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    repository: CampusBuddyRepository,
    onBack: () -> Unit,
    onNavigateToPartnerProfile: (String) -> Unit
) {
    var conversation by remember { mutableStateOf<Conversation?>(null) }
    var partner by remember { mutableStateOf<UserProfile?>(null) }
    var currentUserId by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Real-time messages via Flow
    val messages by repository.getMessagesFlow(conversationId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(conversationId) {
        val user = repository.getCurrentFirebaseUser() ?: return@LaunchedEffect
        currentUserId = user.uid

        repository.getUserConversations(user.uid).onSuccess { convs ->
            conversation = convs.find { it.id == conversationId }
            val partnerId = conversation?.memberIds?.find { it != user.uid }
            if (partnerId != null) {
                repository.getUserProfile(partnerId).onSuccess { partner = it }
            }
        }

        repository.markMessagesAsRead(conversationId, user.uid)
        isLoading = false
    }

    // Scroll to bottom when new messages arrive
    val messageCount = messages.size
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            // Small delay so the layout has settled (IME padding, LazyColumn resize, etc.)
            delay(50)
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    // Scroll to bottom when keyboard appears — read IME insets at composable level (snapshot-aware)
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) {
            // Small delay to let layout settle after IME insets change
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(partner?.fullName ?: "Chat",
                                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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
                        Text("Online", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    partner?.let {
                        IconButton(onClick = { onNavigateToPartnerProfile(it.id) }) {
                            Icon(Icons.Filled.Person, contentDescription = "Profile")
                        }
                    }
                },
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
            // Messages
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages) { message ->
                        val isCurrentUser = message.senderId == currentUserId
                        // Cache formatted timestamp so it's not re-computed on every keystroke recomposition
                        val formattedTime = remember(message.createdAt) {
                            formatMessageTime(message.createdAt)
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = if (isCurrentUser) 16.dp else 4.dp,
                                    topEnd = if (isCurrentUser) 4.dp else 16.dp,
                                    bottomStart = 16.dp,
                                    bottomEnd = 16.dp
                                ),
                                color = if (isCurrentUser) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = message.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = formattedTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Input
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                val textToSend = messageText
                                messageText = "" // Clear UI instantly (optimistic update)
                                scope.launch {
                                    repository.sendMessage(conversationId, currentUserId, textToSend)
                                    // Flow will automatically update messages list
                                }
                            }
                        },
                        enabled = messageText.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private val timeFormatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())

fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "Sending..."
    }
    return timeFormatter.format(java.util.Date(timestamp))
}
