package com.example.campusbuddy.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.models.UserProfile
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import com.example.campusbuddy.ui.setup.*
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(
    repository: CampusBuddyRepository,
    onBack: () -> Unit
) {
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var bio by remember { mutableStateOf("") }
    var selectedInterests by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSkills by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAvailability by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedGoals by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val user = repository.getCurrentFirebaseUser() ?: return@LaunchedEffect
        repository.getUserProfile(user.uid).onSuccess { profile ->
            userProfile = profile
            bio = profile.bio ?: ""
            selectedInterests = profile.interests
            selectedSkills = profile.skills
            selectedAvailability = profile.availability
            selectedGoals = profile.goals
        }
        isLoading = false
    }

    val editScope = rememberCoroutineScope()
    val hasChanges = bio != (userProfile?.bio ?: "") ||
            selectedInterests != userProfile?.interests ||
            selectedSkills != userProfile?.skills ||
            selectedAvailability != userProfile?.availability ||
            selectedGoals != userProfile?.goals

    Scaffold(
        topBar = { AppTopBar(title = "Edit Profile", onBack = onBack) }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Bio", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(value = bio, onValueChange = { bio = it }, label = "Bio",
                    singleLine = false, maxLines = 3)
                Spacer(modifier = Modifier.height(20.dp))

                Text("Interests", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                ChipSelector(options = interestOptions, selectedOptions = selectedInterests,
                    onToggle = { option ->
                        selectedInterests = if (option in selectedInterests) selectedInterests - option
                        else selectedInterests + option
                    })
                Spacer(modifier = Modifier.height(20.dp))

                Text("Skills", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                ChipSelector(options = skillOptions, selectedOptions = selectedSkills,
                    onToggle = { option ->
                        selectedSkills = if (option in selectedSkills) selectedSkills - option
                        else selectedSkills + option
                    })
                Spacer(modifier = Modifier.height(20.dp))

                Text("Availability", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                ChipSelector(options = availabilityOptions, selectedOptions = selectedAvailability,
                    onToggle = { option ->
                        selectedAvailability = if (option in selectedAvailability) selectedAvailability - option
                        else selectedAvailability + option
                    })
                Spacer(modifier = Modifier.height(20.dp))

                Text("Goals", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                ChipSelector(options = goalOptions, selectedOptions = selectedGoals,
                    onToggle = { option ->
                        selectedGoals = if (option in selectedGoals) selectedGoals - option
                        else selectedGoals + option
                    })

                Spacer(modifier = Modifier.height(32.dp))

                AppPrimaryButton(
                    text = "Save Changes",
                    enabled = hasChanges && !isSaving,
                    isLoading = isSaving,
                    onClick = {
                        val user = repository.getCurrentFirebaseUser() ?: return@AppPrimaryButton
                        isSaving = true
                        editScope.launch {
                            repository.updateUserProfile(user.uid, mapOf(
                                "bio" to bio,
                                "interests" to selectedInterests,
                                "skills" to selectedSkills,
                                "availability" to selectedAvailability,
                                "goals" to selectedGoals
                            )).onSuccess {
                                isSaving = false
                                onBack()
                            }.onFailure {
                                isSaving = false
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
