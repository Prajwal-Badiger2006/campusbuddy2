package com.example.campusbuddy.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.enums.NotificationType
import com.example.campusbuddy.data.models.Notification
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.AppTopBar
import com.example.campusbuddy.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    repository: CampusBuddyRepository,
    onBack: () -> Unit,
    onNavigateToMyRequestDetails: (Long) -> Unit,
    onNavigateToChat: (Long) -> Unit,
    onNavigateToMyRequests: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val notifScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val user = repository.getCurrentFirebaseUser() ?: return@LaunchedEffect
        repository.getNotifications(user.uid).onSuccess {
            notifications = it
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Notifications",
                onBack = onBack,
                actions = {
                    if (notifications.any { !it.isRead }) {
                        TextButton(onClick = {
                            val user = repository.getCurrentFirebaseUser() ?: return@TextButton
                            notifScope.launch {
                                repository.markAllNotificationsAsRead(user.uid)
                                notifications = notifications.map { it.copy(isRead = true) }
                            }
                        }) {
                            Text("Mark All Read")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            notifications.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.NotificationsNone,
                    title = "No Notifications",
                    subtitle = "You're all caught up!",
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    items(notifications) { notification ->
                        val icon = when (notification.type) {
                            NotificationType.REQUEST_RECEIVED -> Icons.Filled.Person
                            NotificationType.REQUEST_ACCEPTED -> Icons.Filled.CheckCircle
                            NotificationType.REQUEST_REJECTED -> Icons.Filled.Cancel
                            NotificationType.NEW_MESSAGE -> Icons.Filled.ChatBubble
                            NotificationType.STREAK_MILESTONE -> Icons.Filled.LocalFireDepartment
                        }

                        ListItem(
                            headlineContent = {
                                Text(notification.title, fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = { Text(notification.body) },
                            leadingContent = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (notification.isRead) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (notification.isRead) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.clickable {
                                notifScope.launch {
                                    repository.markNotificationAsRead(notification.id)
                                }
                                // Route based on type
                                when (notification.type) {
                                    NotificationType.REQUEST_RECEIVED -> {
                                        notification.referenceId?.toLongOrNull()?.let {
                                            onNavigateToMyRequestDetails(it)
                                        }
                                    }
                                    NotificationType.REQUEST_ACCEPTED -> {
                                        notification.referenceId?.toLongOrNull()?.let {
                                            onNavigateToChat(it)
                                        }
                                    }
                                    NotificationType.REQUEST_REJECTED -> onNavigateToMyRequests()
                                    NotificationType.NEW_MESSAGE -> {
                                        notification.referenceId?.toLongOrNull()?.let {
                                            onNavigateToChat(it)
                                        }
                                    }
                                    NotificationType.STREAK_MILESTONE -> onNavigateToProfile()
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
