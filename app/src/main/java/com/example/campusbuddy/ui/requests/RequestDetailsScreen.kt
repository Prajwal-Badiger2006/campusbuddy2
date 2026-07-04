package com.example.campusbuddy.ui.requests

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.models.PartnerRequest
import com.example.campusbuddy.data.models.UserProfile
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun RequestDetailsScreen(
    requestId: Long,
    repository: CampusBuddyRepository,
    onBack: () -> Unit,
    onNavigateToPartnerProfile: (String) -> Unit
) {
    var request by remember { mutableStateOf<PartnerRequest?>(null) }
    var creator by remember { mutableStateOf<UserProfile?>(null) }
    var hasResponded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isResponding by remember { mutableStateOf(false) }
    var responseSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val requestScope = rememberCoroutineScope()

    LaunchedEffect(requestId) {
        repository.getPartnerRequestById(requestId).onSuccess { req ->
            request = req
            repository.getUserProfile(req.creatorId).onSuccess { creator = it }
            val user = repository.getCurrentFirebaseUser()
            if (user != null) {
                repository.getResponsesForRequest(requestId).onSuccess { responses ->
                    hasResponded = responses.any { it.responderId == user.uid }
                }
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = { AppTopBar(title = "Request Details", onBack = onBack) }
    ) { innerPadding ->
        if (isLoading) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                SkeletonCard(); Spacer(modifier = Modifier.height(12.dp)); SkeletonCard()
            }
        } else if (request == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Request not found")
            }
        } else {
            val req = request!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                RequestTypeBadge(req.type)
                Spacer(modifier = Modifier.height(12.dp))
                Text(req.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

                if (!req.subjectOrTopic.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Subject: ${req.subjectOrTopic}", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!req.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(req.description, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))
                AppDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text("Range", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(req.preferredRange.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text("Partners Needed", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${req.neededCount}", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (req.availability != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Availability", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(req.availability!!, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Creator Card
                Text("Posted by", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                creator?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        onClick = { onNavigateToPartnerProfile(it.id) }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(photoUrl = it.profilePhotoUrl, name = it.fullName)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(it.fullName, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("${it.department} · ${it.year}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (it.reliabilityScore > 0) {
                                Text("${it.reliabilityScore.toInt()}%", style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Respond button
                when {
                    hasResponded || responseSuccess -> {
                        AppSecondaryButton(
                            text = "Already Requested ✓",
                            onClick = {},
                            enabled = false
                        )
                    }
                    else -> {
                        AppPrimaryButton(
                            text = "Request to Join",
                            isLoading = isResponding,
                            onClick = {
                                val user = repository.getCurrentFirebaseUser() ?: return@AppPrimaryButton
                                if (user.uid == req.creatorId) {
                                    errorMessage = "You cannot respond to your own request"
                                    return@AppPrimaryButton
                                }
                                isResponding = true
                                requestScope.launch {
                                    repository.respondToRequest(requestId, user.uid).onSuccess {
                                        isResponding = false
                                        responseSuccess = true
                                    }.onFailure {
                                        isResponding = false
                                        errorMessage = "Failed to send request"
                                    }
                                }
                            }
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
