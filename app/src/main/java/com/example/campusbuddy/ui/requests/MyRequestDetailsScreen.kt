package com.example.campusbuddy.ui.requests

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.enums.ResponseStatus
import com.example.campusbuddy.data.models.PartnerRequest
import com.example.campusbuddy.data.models.PartnerResponse
import com.example.campusbuddy.data.models.UserProfile
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun MyRequestDetailsScreen(
    requestId: Long,
    repository: CampusBuddyRepository,
    onBack: () -> Unit,
    onNavigateToPartnerProfile: (String) -> Unit,
    onNavigateToChat: (Long) -> Unit
) {
    var request by remember { mutableStateOf<PartnerRequest?>(null) }
    var responses by remember { mutableStateOf<List<PartnerResponse>>(emptyList()) }
    var responders by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var showAcceptDialog by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val myRequestScope = rememberCoroutineScope()

    LaunchedEffect(requestId) {
        repository.getPartnerRequestById(requestId).onSuccess { req ->
            request = req
            repository.getResponsesForRequest(requestId).onSuccess { responseList ->
                responses = responseList
                responseList.forEach { resp ->
                    repository.getUserProfile(resp.responderId).onSuccess { profile ->
                        responders = responders + (resp.responderId to profile)
                    }
                }
            }
        }
        isLoading = false
    }

    if (showCloseDialog) {
        ConfirmationDialog(
            title = "Close Request",
            message = "Are you sure you want to close this request? It will no longer accept new responses.",
            confirmLabel = "Close",
            onConfirm = {
                myRequestScope.launch {
                    repository.closeRequest(requestId)
                    request = request?.copy(status = com.example.campusbuddy.data.enums.RequestStatus.CLOSED)
                }
                showCloseDialog = false
            },
            onDismiss = { showCloseDialog = false }
        )
    }

    showAcceptDialog?.let { (responseId, responderId) ->
        ConfirmationDialog(
            title = "Accept Response",
            message = "Accept this partner? A match and conversation will be created.",
            confirmLabel = "Accept",
            onConfirm = {
                if (isProcessing) return@ConfirmationDialog
                isProcessing = true
                val response = responses.find { it.id == responseId }
                if (response != null) {
                    myRequestScope.launch {
                        repository.acceptResponse(response, requestId)
                            .onSuccess {
                                // Update local state to immediately show it as accepted
                                responses = responses.map {
                                    if (it.id == responseId) it.copy(status = ResponseStatus.ACCEPTED) else it
                                }
                                request = request?.copy(acceptedCount = (request?.acceptedCount ?: 0) + 1)
                            }
                            .onFailure {
                                snackbarMessage = "Failed to accept. Please try again."
                            }
                        isProcessing = false
                    }
                } else {
                    isProcessing = false
                }
                showAcceptDialog = null
            },
            onDismiss = { showAcceptDialog = null }
        )
    }

    // Show snackbar when message is set
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = { AppTopBar(title = "My Request", onBack = onBack) }
    ) { innerPadding ->
        if (isLoading) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                repeat(3) { SkeletonCard(modifier = Modifier.padding(bottom = 12.dp)) }
            }
        } else if (request == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Request not found")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RequestTypeBadge(request!!.type)
                        StatusBadge(request!!.status)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(request!!.title, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold)
                    Text("${request!!.acceptedCount}/${request!!.neededCount} accepted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (request!!.acceptedCount >= request!!.neededCount)
                            MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)

                    if (request!!.status == com.example.campusbuddy.data.enums.RequestStatus.OPEN) {
                        Spacer(modifier = Modifier.height(16.dp))
                        AppSecondaryButton(text = "Close Request", onClick = { showCloseDialog = true })
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Responses (${responses.size})", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                }

                if (responses.isEmpty()) {
                    item {
                        Text("No responses yet", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(responses) { response ->
                    val responder = responders[response.responderId]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                responder?.let {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        UserAvatar(photoUrl = it.profilePhotoUrl, name = it.fullName, size = 40)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(it.fullName, style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold)
                                            Text(it.department, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            when (response.status) {
                                ResponseStatus.PENDING -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { showAcceptDialog = Pair(response.id, response.responderId) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                            ),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Accept")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                myRequestScope.launch {
                                                    repository.rejectResponse(response).onSuccess {
                                                        responses = responses.map {
                                                            if (it.id == response.id) it.copy(status = ResponseStatus.REJECTED) else it
                                                        }
                                                    }
                                                }
                                            },
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                        ) {
                                            Icon(Icons.Filled.Cancel, contentDescription = null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reject")
                                        }
                                    }
                                }
                                ResponseStatus.ACCEPTED -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("✓ Accepted", color = MaterialTheme.colorScheme.secondary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        TextButton(onClick = {
                                            // Find conversation
                                        }) {
                                            Text("Open Chat")
                                        }
                                    }
                                }
                                ResponseStatus.REJECTED -> {
                                    Text("✗ Rejected", color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}
