package com.example.campusbuddy.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.components.*
import com.example.campusbuddy.ui.theme.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private enum class SignupStep(val stepNumber: Int, val title: String) {
    PERSONAL(0, "Personal Info"),
    ACADEMIC(1, "Academic Info"),
    SECURITY(2, "Security")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    repository: CampusBuddyRepository,
    onNavigateToEmailVerification: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    // Form state
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var regNumber by remember { mutableStateOf("") }
    var collegeName by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Validation states
    var nameError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var regError by remember { mutableStateOf(false) }
    var collegeError by remember { mutableStateOf(false) }
    var deptError by remember { mutableStateOf(false) }
    var yearError by remember { mutableStateOf(false) }
    var passError by remember { mutableStateOf(false) }
    var confirmPassError by remember { mutableStateOf(false) }

    // Real-time validation
    var emailFormatValid by remember { mutableStateOf(false) }
    var passwordMinLength by remember { mutableStateOf(false) }
    var passwordHasUpper by remember { mutableStateOf(false) }
    var passwordHasNumber by remember { mutableStateOf(false) }
    var passwordsMatch by remember { mutableStateOf(false) }

    // Step management
    var currentStep by remember { mutableStateOf(SignupStep.PERSONAL) }
    val isLastStep = currentStep == SignupStep.SECURITY

    val collegeOptions = listOf("Stanford University", "MIT", "Harvard University", "UC Berkeley", "Oxford University", "Cambridge University")
    val departmentOptions = listOf("Computer Science", "Engineering", "Business", "Arts & Humanities", "Natural Sciences", "Medicine")
    val yearOptions = listOf("1st", "2nd", "3rd", "4th")
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Focus requesters for keyboard navigation
    val nameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val regFocus = remember { FocusRequester() }
    val collegeFocus = remember { FocusRequester() }
    val deptFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    val confirmPassFocus = remember { FocusRequester() }

    val scrollState = rememberScrollState()

    // Real-time validation effects
    LaunchedEffect(email) {
        delay(300) // debounce
        emailFormatValid = email.contains("@") && email.contains(".") && email.length >= 6
        if (email.isNotEmpty() && !emailFormatValid) emailError = true
        else if (emailFormatValid) emailError = false
    }

    LaunchedEffect(password) {
        passwordMinLength = password.length >= 8
        passwordHasUpper = password.any { it.isUpperCase() }
        passwordHasNumber = password.any { it.isDigit() }
        if (password.isNotEmpty()) {
            passError = password.length < 8
        } else {
            passError = false
        }
    }

    LaunchedEffect(confirmPassword, password) {
        passwordsMatch = confirmPassword == password && confirmPassword.isNotEmpty()
        if (confirmPassword.isNotEmpty()) {
            confirmPassError = !passwordsMatch
        } else {
            confirmPassError = false
        }
    }

    // Validation for current step
    fun validateStep(): Boolean {
        return when (currentStep) {
            SignupStep.PERSONAL -> {
                var valid = true
                if (fullName.isBlank()) { nameError = true; valid = false }
                if (email.isBlank() || !emailFormatValid) { emailError = true; valid = false }
                if (regNumber.isBlank()) { regError = true; valid = false }
                valid
            }
            SignupStep.ACADEMIC -> {
                var valid = true
                if (collegeName.isBlank()) { collegeError = true; valid = false }
                if (department.isBlank()) { deptError = true; valid = false }
                if (year.isBlank()) { yearError = true; valid = false }
                valid
            }
            SignupStep.SECURITY -> {
                var valid = true
                if (password.length < 8) { passError = true; valid = false }
                if (confirmPassword != password) { confirmPassError = true; valid = false }
                valid
            }
        }
    }

    fun handleNext() {
        if (!validateStep()) return
        when (currentStep) {
            SignupStep.PERSONAL -> currentStep = SignupStep.ACADEMIC
            SignupStep.ACADEMIC -> currentStep = SignupStep.SECURITY
            SignupStep.SECURITY -> { /* submit handled below */ }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Create Account") },
            navigationIcon = {
                IconButton(onClick = {
                    if (currentStep == SignupStep.PERSONAL) {
                        onNavigateToLogin()
                    } else {
                        currentStep = when (currentStep) {
                            SignupStep.ACADEMIC -> SignupStep.PERSONAL
                            SignupStep.SECURITY -> SignupStep.ACADEMIC
                            else -> SignupStep.PERSONAL
                        }
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .imePadding()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Step indicator
            StepIndicator(currentStep = currentStep)

            Spacer(modifier = Modifier.height(8.dp))

            // Step title
            Text(
                text = when (currentStep) {
                    SignupStep.PERSONAL -> "Tell us about yourself"
                    SignupStep.ACADEMIC -> "Your academic details"
                    SignupStep.SECURITY -> "Secure your account"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when (currentStep) {
                    SignupStep.PERSONAL -> "Start your journey to find the perfect study partner."
                    SignupStep.ACADEMIC -> "Help us find students from your college and department."
                    SignupStep.SECURITY -> "Create a strong password to keep your account safe."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Animated step content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val direction = if (targetState.stepNumber > initialState.stepNumber) 1 else -1
                    slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { fullWidth -> direction * fullWidth / 4 }
                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                            slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { fullWidth -> -direction * fullWidth / 4 }
                            ) + fadeOut(animationSpec = tween(300))
                },
                label = "stepContent"
            ) { step ->
                Column {
                    when (step) {
                        SignupStep.PERSONAL -> PersonalInfoStep(
                            fullName = fullName,
                            onNameChange = { fullName = it; nameError = false },
                            nameError = nameError,
                            email = email,
                            onEmailChange = { email = it; errorMessage = null },
                            emailError = emailError,
                            emailFormatValid = emailFormatValid,
                            regNumber = regNumber,
                            onRegChange = { regNumber = it; regError = false },
                            regError = regError,
                            nameFocus = nameFocus,
                            emailFocus = emailFocus,
                            regFocus = regFocus,
                            onNext = {
                                focusManager.clearFocus()
                                handleNext()
                            }
                        )

                        SignupStep.ACADEMIC -> AcademicInfoStep(
                            collegeName = collegeName,
                            onCollegeChange = { collegeName = it; collegeError = false },
                            collegeError = collegeError,
                            collegeOptions = collegeOptions,
                            department = department,
                            onDepartmentChange = { department = it; deptError = false },
                            deptError = deptError,
                            departmentOptions = departmentOptions,
                            year = year,
                            onYearChange = { year = it; yearError = false },
                            yearError = yearError,
                            yearOptions = yearOptions,
                            collegeFocus = collegeFocus,
                            deptFocus = deptFocus,
                            onNext = {
                                focusManager.clearFocus()
                                handleNext()
                            }
                        )

                        SignupStep.SECURITY -> SecurityStep(
                            password = password,
                            onPasswordChange = { password = it },
                            passError = passError,
                            passwordMinLength = passwordMinLength,
                            passwordHasUpper = passwordHasUpper,
                            passwordHasNumber = passwordHasNumber,
                            confirmPassword = confirmPassword,
                            onConfirmPasswordChange = { confirmPassword = it },
                            confirmPassError = confirmPassError,
                            passwordsMatch = passwordsMatch,
                            passFocus = passFocus,
                            confirmPassFocus = confirmPassFocus
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep != SignupStep.PERSONAL) {
                    OutlinedButton(
                        onClick = {
                            currentStep = when (currentStep) {
                                SignupStep.ACADEMIC -> SignupStep.PERSONAL
                                SignupStep.SECURITY -> SignupStep.ACADEMIC
                                else -> SignupStep.PERSONAL
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Back", style = MaterialTheme.typography.labelLarge)
                    }
                }

                AppPrimaryButton(
                    text = if (isLastStep) "Create Account" else "Continue",
                    onClick = {
                        if (isLastStep) {
                            focusManager.clearFocus()
                            if (validateStep()) {
                                isLoading = true
                                scope.launch {
                                    try {
                                        withTimeout(60_000) {
                                            val result = repository.signup(
                                                fullName, email, password, regNumber,
                                                collegeName, department, year
                                            )
                                            result.onSuccess {
                                                isLoading = false
                                                onNavigateToEmailVerification()
                                            }.onFailure { e ->
                                                isLoading = false
                                                errorMessage = e.message ?: "Signup failed"
                                            }
                                        }
                                    } catch (e: TimeoutCancellationException) {
                                        isLoading = false
                                        errorMessage = "Request timed out. Please check your internet connection and try again."
                                    }
                                }
                            }
                        } else if (currentStep == SignupStep.PERSONAL) {
                            // On page 1 — validate fields, then check if email is already registered
                            if (!validateStep()) return@AppPrimaryButton
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    withTimeout(15_000) {
                                        val emailExists = repository.checkEmailExists(email)
                                        if (emailExists) {
                                            isLoading = false
                                            errorMessage = "This email is already registered. Try signing in instead."
                                            emailError = true
                                        } else {
                                            isLoading = false
                                            currentStep = SignupStep.ACADEMIC
                                        }
                                    }
                                } catch (e: TimeoutCancellationException) {
                                    isLoading = false
                                    // On timeout, let the user proceed — the actual signup will catch duplicates
                                    currentStep = SignupStep.ACADEMIC
                                }
                            }
                        } else {
                            handleNext()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    isLoading = isLoading
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Already have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onNavigateToLogin) {
                    Text(
                        "Sign In",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ==========================================
// STEP INDICATOR
// ==========================================
@Composable
private fun StepIndicator(currentStep: SignupStep) {
    val steps = SignupStep.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val isActive = step == currentStep
            val isCompleted = step.stepNumber < currentStep.stepNumber

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // Step circle
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(50),
                    color = when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCompleted) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = "${step.stepNumber + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive || isCompleted) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // Connector line between steps
            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(bottom = 20.dp)
                        .height(2.dp)
                        .background(
                            color = if (step.stepNumber < currentStep.stepNumber)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}

// ==========================================
// STEP 1: PERSONAL INFO
// ==========================================
@Composable
private fun PersonalInfoStep(
    fullName: String,
    onNameChange: (String) -> Unit,
    nameError: Boolean,
    email: String,
    onEmailChange: (String) -> Unit,
    emailError: Boolean,
    emailFormatValid: Boolean,
    regNumber: String,
    onRegChange: (String) -> Unit,
    regError: Boolean,
    nameFocus: FocusRequester,
    emailFocus: FocusRequester,
    regFocus: FocusRequester,
    onNext: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Section header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Personal Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppTextField(
                value = fullName,
                onValueChange = onNameChange,
                label = "Full Name",
                isError = nameError,
                errorMessage = if (nameError) "Please enter your name" else null,
                imeAction = ImeAction.Next,
                onImeAction = { emailFocus.requestFocus() },
                leadingIcon = Icons.Filled.Person,
                modifier = Modifier.focusRequester(nameFocus)
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppTextField(
                value = email,
                onValueChange = onEmailChange,
                label = "Email Address",
                placeholder = "you@email.com",
                isError = emailError,
                errorMessage = when {
                    email.isNotEmpty() && !emailFormatValid -> "Enter a valid email address"
                    emailError && email.isBlank() -> "Email is required"
                    else -> null
                },
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                onImeAction = { regFocus.requestFocus() },
                leadingIcon = Icons.Filled.Email,
                trailingIcon = if (email.isNotEmpty()) {
                    if (emailFormatValid) Icons.Filled.CheckCircle else Icons.Filled.Cancel
                } else null,
                onTrailingIconClick = {},
                modifier = Modifier.focusRequester(emailFocus)
            )

            // Real-time email hint
            if (email.isNotEmpty() && !emailFormatValid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Email must include @ and a domain (e.g., .com)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppTextField(
                value = regNumber,
                onValueChange = onRegChange,
                label = "Registration Number",
                isError = regError,
                errorMessage = if (regError) "Registration number is required" else null,
                imeAction = ImeAction.Done,
                onImeAction = onNext,
                leadingIcon = Icons.Filled.Badge,
                modifier = Modifier.focusRequester(regFocus)
            )
        }
    }
}

// ==========================================
// STEP 2: ACADEMIC INFO
// ==========================================
@Composable
private fun AcademicInfoStep(
    collegeName: String,
    onCollegeChange: (String) -> Unit,
    collegeError: Boolean,
    collegeOptions: List<String>,
    department: String,
    onDepartmentChange: (String) -> Unit,
    deptError: Boolean,
    departmentOptions: List<String>,
    year: String,
    onYearChange: (String) -> Unit,
    yearError: Boolean,
    yearOptions: List<String>,
    collegeFocus: FocusRequester,
    deptFocus: FocusRequester,
    onNext: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Section header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Academic Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppDropdownField(
                value = collegeName,
                onValueChange = onCollegeChange,
                label = "College Name",
                options = collegeOptions,
                isError = collegeError,
                errorMessage = if (collegeError) "Please select your college" else null
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppDropdownField(
                value = department,
                onValueChange = onDepartmentChange,
                label = "Department",
                options = departmentOptions,
                isError = deptError,
                errorMessage = if (deptError) "Please select your department" else null
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Year selection with Choice Chips
            Text(
                text = "Year",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                yearOptions.forEach { option ->
                    val isSelected = year == option
                    val chipBorderColor = when {
                        isSelected -> Color.Transparent
                        yearError -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onYearChange(option) },
                        label = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected || yearError) 1.5.dp else 1.dp,
                            color = chipBorderColor
                        ),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (year.isBlank() && !yearError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select your current year",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (yearError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Please select your current year",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ==========================================
// STEP 3: SECURITY
// ==========================================
@Composable
private fun SecurityStep(
    password: String,
    onPasswordChange: (String) -> Unit,
    passError: Boolean,
    passwordMinLength: Boolean,
    passwordHasUpper: Boolean,
    passwordHasNumber: Boolean,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    confirmPassError: Boolean,
    passwordsMatch: Boolean,
    passFocus: FocusRequester,
    confirmPassFocus: FocusRequester
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Section header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Security",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppPasswordField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Password",
                isError = passError,
                errorMessage = if (passError) "Password must be at least 8 characters" else null
            )

            // Real-time password checklist
            if (password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Password must include:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PasswordRequirement(
                        text = "At least 8 characters",
                        met = passwordMinLength
                    )
                    PasswordRequirement(
                        text = "At least one uppercase letter",
                        met = passwordHasUpper
                    )
                    PasswordRequirement(
                        text = "At least one number",
                        met = passwordHasNumber
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppPasswordField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = "Confirm Password",
                isError = confirmPassError,
                errorMessage = when {
                    confirmPassword.isNotEmpty() && !passwordsMatch -> "Passwords do not match"
                    confirmPassError && confirmPassword.isBlank() -> "Please confirm your password"
                    else -> null
                },
                modifier = Modifier.focusRequester(confirmPassFocus)
            )

            // Real-time password match indicator
            if (confirmPassword.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (passwordsMatch) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (passwordsMatch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (passwordsMatch) "Passwords match" else "Passwords do not match",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (passwordsMatch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ==========================================
// PASSWORD REQUIREMENT ROW
// ==========================================
@Composable
private fun PasswordRequirement(
    text: String,
    met: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (met) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (met) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (met) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = if (met) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
