package com.example.campusbuddy.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.local.UserPreferences
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import kotlinx.coroutines.launch

val interestOptions = listOf(
    "DSA", "Web Dev", "ML", "AI", "UI/UX", "Robotics",
    "Competitive Programming", "Public Speaking", "Photography",
    "Music", "Dance", "Sports", "Entrepreneurship", "Design"
)

val skillOptions = listOf(
    "Python", "Java", "Kotlin", "JavaScript", "C++", "SQL",
    "Git", "Figma", "Communication", "Leadership", "Research"
)

val availabilityOptions = listOf(
    "Morning", "Afternoon", "Evening", "Night", "Weekdays", "Weekends"
)

val goalOptions = listOf(
    "Crack Placements", "Pass Exams", "Build Projects",
    "Win Hackathons", "Learn New Skills", "Participate in Events"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    repository: CampusBuddyRepository,
    onNavigateToOnboarding: () -> Unit
) {
    var bio by remember { mutableStateOf("") }
    var selectedInterests by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSkills by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAvailability by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedGoals by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val canSave = selectedInterests.size >= 2 && selectedAvailability.size >= 1
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Complete Your Profile") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            Text(
                text = "Set up your profile to get the best matches",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Bio
            Text("Bio (optional)", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            AppTextField(
                value = bio,
                onValueChange = { bio = it },
                label = "Tell us about yourself",
                singleLine = false,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Interests
            Text(
                text = "Interests (select at least 2)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChipSelector(
                options = interestOptions,
                selectedOptions = selectedInterests,
                onToggle = { option ->
                    selectedInterests = if (option in selectedInterests) {
                        selectedInterests - option
                    } else {
                        selectedInterests + option
                    }
                }
            )
            Text(
                text = "${selectedInterests.size} selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Skills
            Text("Skills", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            ChipSelector(
                options = skillOptions,
                selectedOptions = selectedSkills,
                onToggle = { option ->
                    selectedSkills = if (option in selectedSkills) {
                        selectedSkills - option
                    } else {
                        selectedSkills + option
                    }
                }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Availability
            Text(
                text = "Availability (select at least 1)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChipSelector(
                options = availabilityOptions,
                selectedOptions = selectedAvailability,
                onToggle = { option ->
                    selectedAvailability = if (option in selectedAvailability) {
                        selectedAvailability - option
                    } else {
                        selectedAvailability + option
                    }
                }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Goals
            Text("Goals", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            ChipSelector(
                options = goalOptions,
                selectedOptions = selectedGoals,
                onToggle = { option ->
                    selectedGoals = if (option in selectedGoals) {
                        selectedGoals - option
                    } else {
                        selectedGoals + option
                    }
                }
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Save button — saves profile and navigates to onboarding
            AppPrimaryButton(
                text = "Save & Continue",
                onClick = {
                    scope.launch {
                        isLoading = true
                        val user = repository.getCurrentFirebaseUser()
                        if (user != null) {
                            val updates = mutableMapOf<String, Any>(
                                "bio" to bio,
                                "interests" to selectedInterests,
                                "skills" to selectedSkills,
                                "availability" to selectedAvailability,
                                "goals" to selectedGoals
                            )
                            repository.updateUserProfile(user.uid, updates)
                        }
                        // Reset verification popup state so it shows for new users
                        userPreferences.resetVerificationPopup()
                        isLoading = false
                        onNavigateToOnboarding()
                    }
                },
                enabled = canSave && !isLoading,
                isLoading = isLoading
            )

            if (!canSave) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select at least 2 interests and 1 availability slot to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
