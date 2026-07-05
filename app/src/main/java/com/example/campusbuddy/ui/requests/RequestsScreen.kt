package com.example.campusbuddy.ui.requests

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.enums.RequestStatus
import com.example.campusbuddy.data.models.PartnerRequest
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    repository: CampusBuddyRepository,
    onNavigateToCreateRequest: () -> Unit,
    onNavigateToRequestDetails: (Long) -> Unit,
    onNavigateToMyRequestDetails: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var browseRequests by remember { mutableStateOf<List<PartnerRequest>>(emptyList()) }
    var myRequests by remember { mutableStateOf<List<PartnerRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCurrentUserVerified by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        isLoading = true
        val user = repository.getCurrentFirebaseUser() ?: return@LaunchedEffect
        val profile = repository.getUserProfile(user.uid).getOrNull()
        isCurrentUserVerified = profile?.isVerifiedStudent == true

        if (selectedTab == 0) {
            repository.getPartnerRequests(profile?.collegeName ?: "").onSuccess {
                browseRequests = it; isLoading = false
            }.onFailure { errorMessage = it.message; isLoading = false }
        } else {
            repository.getUserRequests(user.uid).onSuccess {
                myRequests = it; isLoading = false
            }.onFailure { errorMessage = it.message; isLoading = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Requests") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreateRequest,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Browse Requests") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("My Requests") })
            }

            when {
                isLoading -> {
                    Column(modifier = Modifier.padding(16.dp)) {
                        repeat(3) { SkeletonCard(modifier = Modifier.padding(bottom = 12.dp)) }
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                selectedTab == 0 -> {
                    if (browseRequests.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.SentimentDissatisfied,
                            title = "No Open Requests",
                            subtitle = "No open requests found. Create one!"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(browseRequests) { request ->
                                RequestCard(
                                    title = request.title,
                                    type = request.type,
                                    subjectOrTopic = request.subjectOrTopic,
                                    creatorName = "Student",
                                    department = "",
                                    neededCount = request.neededCount,
                                    acceptedCount = request.acceptedCount,
                                    postedTime = formatTimeAgo(request.createdAt),
                                    onClick = { onNavigateToRequestDetails(request.id) }
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (myRequests.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.SentimentDissatisfied,
                            title = "No Requests Yet",
                            subtitle = "You have not created any requests yet."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(myRequests) { request ->
                                RequestCard(
                                    title = request.title,
                                    type = request.type,
                                    subjectOrTopic = request.subjectOrTopic,
                                    creatorName = "You",
                                    department = "",
                                    neededCount = request.neededCount,
                                    acceptedCount = request.acceptedCount,
                                    postedTime = formatTimeAgo(request.createdAt),
                                    onClick = { onNavigateToMyRequestDetails(request.id) },
                                    showStatus = true,
                                    status = request.status,
                                    isVerifiedCreator = isCurrentUserVerified
                                )
                            }
                        }
                    }
                }
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
