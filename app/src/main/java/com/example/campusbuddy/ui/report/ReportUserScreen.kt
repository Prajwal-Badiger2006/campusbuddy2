package com.example.campusbuddy.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportUserScreen(
    targetUserId: String,
    targetUserName: String,
    repository: CampusBuddyRepository,
    onBack: () -> Unit
) {
    var reasonExpanded by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    val reportScope = rememberCoroutineScope()

    val reasons = listOf("Spam", "Harassment", "Fake Account", "Inappropriate Content", "Other")

    Scaffold(
        topBar = { AppTopBar(title = "Report User", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Flag,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Report $targetUserName",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your report will be reviewed by our team. Reports are confidential.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Reason for Report",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = reasonExpanded,
                onExpandedChange = { reasonExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedReason ?: "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select a reason") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                ExposedDropdownMenu(
                    expanded = reasonExpanded,
                    onDismissRequest = { reasonExpanded = false }
                ) {
                    reasons.forEach { reason ->
                        DropdownMenuItem(
                            text = { Text(reason) },
                            onClick = { selectedReason = reason; reasonExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(
                value = description,
                onValueChange = { description = it },
                label = "Additional Details (optional)",
                singleLine = false,
                maxLines = 4
            )

            if (showSuccess) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Report submitted successfully. Our team will review it.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AppPrimaryButton(
                text = if (showSuccess) "Done" else "Submit Report",
                enabled = selectedReason != null && !isLoading,
                isLoading = isLoading,
                onClick = {
                    if (showSuccess) {
                        onBack()
                        return@AppPrimaryButton
                    }
                    isLoading = true
                    reportScope.launch {
                        repository.reportUser(
                            targetUserId,
                            selectedReason ?: "",
                            description.ifBlank { null }
                        )
                        isLoading = false
                        showSuccess = true
                    }
                }
            )
        }
    }
}
