package com.example.campusbuddy.ui.requests

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.enums.RangeType
import com.example.campusbuddy.data.enums.RequestType
import com.example.campusbuddy.data.models.PartnerRequest
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun CreateRequestScreen(
    repository: CampusBuddyRepository,
    onBack: () -> Unit
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf<RequestType?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var rangeExpanded by remember { mutableStateOf(false) }
    var selectedRange by remember { mutableStateOf<RangeType?>(null) }
    var selectedAvail by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSkills by remember { mutableStateOf<List<String>>(emptyList()) }
    var neededCount by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    val createScope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canPost = selectedType != null && title.length >= 5 && selectedRange != null && selectedAvail.isNotEmpty()

    val availOptions = listOf("Morning", "Afternoon", "Evening", "Night", "Weekdays", "Weekends")
    val skillOptions = listOf("Python", "Java", "Kotlin", "JavaScript", "C++", "SQL", "Git", "Figma")

    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = { AppTopBar(title = "Create Request", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Request Type
            Text("Request Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                OutlinedTextField(
                    value = selectedType?.displayName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(4.dp)
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    RequestType.entries.forEach { type ->
                        DropdownMenuItem(text = { Text(type.displayName) },
                            onClick = { selectedType = type; typeExpanded = false })
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(value = title, onValueChange = { title = it }, label = "Title",
                placeholder = "Looking for a study partner...", imeAction = ImeAction.Next)
            if (title.isNotEmpty() && title.length < 5) {
                Text("Minimum 5 characters", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(12.dp))

            AppTextField(value = description, onValueChange = { description = it },
                label = "Description (optional)", singleLine = false, maxLines = 3)
            Spacer(modifier = Modifier.height(12.dp))

            AppTextField(value = subject, onValueChange = { subject = it },
                label = "Subject/Topic (optional)", imeAction = ImeAction.Next)
            Spacer(modifier = Modifier.height(16.dp))

            // Range
            Text("Preferred Range", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = rangeExpanded, onExpandedChange = { rangeExpanded = it }) {
                OutlinedTextField(
                    value = selectedRange?.displayName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select range") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rangeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(4.dp)
                )
                ExposedDropdownMenu(expanded = rangeExpanded, onDismissRequest = { rangeExpanded = false }) {
                    RangeType.entries.forEach { range ->
                        DropdownMenuItem(text = { Text(range.displayName) },
                            onClick = { selectedRange = range; rangeExpanded = false })
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Availability
            Text("Availability", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            ChipSelector(options = availOptions, selectedOptions = selectedAvail, onToggle = { option ->
                selectedAvail = if (option in selectedAvail) selectedAvail - option else selectedAvail + option
            })
            Spacer(modifier = Modifier.height(16.dp))

            // Preferred Skills
            Text("Preferred Skills (optional)", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ChipSelector(options = skillOptions, selectedOptions = selectedSkills, onToggle = { option ->
                selectedSkills = if (option in selectedSkills) selectedSkills - option else selectedSkills + option
            })
            Spacer(modifier = Modifier.height(16.dp))

            // Needed Count
            Text("Partners Needed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (neededCount > 1) neededCount-- }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                Text("$neededCount", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(onClick = { if (neededCount < 10) neededCount++ }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(24.dp))

            AppPrimaryButton(
                text = "Post Request",
                enabled = canPost && !isLoading,
                isLoading = isLoading,
                onClick = {
                    val user = repository.getCurrentFirebaseUser() ?: return@AppPrimaryButton
                    isLoading = true
                    createScope.launch {
                        val request = PartnerRequest(
                            creatorId = user.uid,
                            type = selectedType!!,
                            title = title,
                            description = description.ifBlank { null },
                            subjectOrTopic = subject.ifBlank { null },
                            preferredRange = selectedRange!!,
                            preferredSkills = selectedSkills,
                            availability = selectedAvail.joinToString(", "),
                            neededCount = neededCount
                        )
                        repository.createPartnerRequest(request).onSuccess {
                            isLoading = false
                            onBack()
                        }.onFailure {
                            isLoading = false
                            errorMessage = "Could not post request. Please try again."
                        }
                    }
                }
            )
        }
    }
}
