# Advanced OCR Implementation Guide

To achieve a 100% accurate and robust OCR system, we need to fix the current bugs and introduce a **User Review** step. No OCR engine in the world is perfect, so the only way to guarantee 100% accuracy is to let the user manually correct any mistakes the OCR makes before saving it to the database.

Here is the step-by-step guide to implement this.

## 1. Fix the `OcrProcessor.kt` Logic Bugs

The current year calculation is flawed. It assumes the last two digits of a year are the student's year of study. Replace `normalizeYear` with a robust version that compares the scanned year against the current year:

```kotlin
private fun normalizeYear(raw: String): String {
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    return when {
        raw.matches(Regex("^20\\d{2}$")) -> {
            val startYear = raw.toInt()
            val diff = currentYear - startYear
            when {
                diff <= 0 -> "1st"
                diff == 1 -> "2nd"
                diff == 2 -> "3rd"
                else -> "4th"
            }
        }
        raw == "I" || raw == "1" -> "1st"
        raw == "II" || raw == "2" -> "2nd"
        raw == "III" || raw == "3" -> "3rd"
        raw == "IV" || raw == "4" -> "4th"
        else -> raw.trim()
    }
}
```

## 2. Fix the Crash in `ScanIdScreen.kt`

Currently, `imageProxy.close()` is called twice, which will crash the app. Remove the first `.close()` call inside `processCapturedImage`.

```diff
  private suspend fun processCapturedImage(...) {
      try {
          val bitmap = withContext(Dispatchers.IO) {
              imageProxyToBitmap(imageProxy)
          }
-         imageProxy.close() // REMOVE THIS LINE!
```

## 3. Implement the "Review & Edit" UI

Instead of instantly saving the data to Firebase and bypassing the user, show them a confirmation dialog containing the extracted data. This allows them to fix typos.

Add this state to `ScanIdScreen.kt`:
```kotlin
var showReviewDialog by remember { mutableStateOf(false) }
var finalScannedData by remember { mutableStateOf<StudentIdData?>(null) }
```

When the back side is successfully scanned, update the flow:
```kotlin
// Inside processCapturedImage onResult:
if (scanSide == ScanSide.FRONT) {
    // ... same as before
} else {
    // Show the review dialog instead of saving immediately
    isProcessing = false
    statusText = "Scan complete! Please review your details."
    finalScannedData = studentIdData
    showReviewDialog = true
}
```

Then, create the Dialog UI at the bottom of your Scaffold:
```kotlin
if (showReviewDialog && finalScannedData != null) {
    var editableName by remember { mutableStateOf(finalScannedData!!.fullName) }
    var editableReg by remember { mutableStateOf(finalScannedData!!.registrationNumber) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* Force them to confirm or cancel */ },
        title = { Text("Review Your Details") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Please correct any mistakes made by the scanner.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editableName,
                    onValueChange = { editableName = it },
                    label = { Text("Full Name") }
                )
                OutlinedTextField(
                    value = editableReg,
                    onValueChange = { editableReg = it },
                    label = { Text("Registration Number") }
                )
                // Add OutlinedTextFields for Department, Year, and Email here...
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    isSaving = true
                    scope.launch {
                        // 4. Proper Backend Saving (See Step 4 below)
                    }
                }
            ) {
                Text(if (isSaving) "Saving..." else "Confirm & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { 
                showReviewDialog = false
                scanSide = ScanSide.FRONT // restart scan
            }) { Text("Rescan") }
        }
    )
}
```

## 4. Proper Backend Saving (Handling Errors)

When the user clicks "Confirm & Save", safely update Firebase and handle network failures so they aren't marked as verified if the network fails.

```kotlin
// Inside the confirmButton onClick from Step 3:
scope.launch {
    val user = repository.getCurrentFirebaseUser()
    if (user != null) {
        val updates = mapOf(
            "isVerifiedStudent" to true,
            "hasScannedId" to true,
            "fullName" to editableName,
            "registrationNumber" to editableReg
            // ... include other edited fields mapped from your text fields
        )
        
        // Wait for the result!
        val result = repository.updateUserProfile(user.uid, updates)
        
        if (result.isSuccess) {
            showReviewDialog = false
            onScanComplete() // Success! Navigate away.
        } else {
            isSaving = false
            statusText = "Network Error: Could not save profile."
        }
    }
}
```

### Summary of Benefits:
- **100% Accuracy:** Users act as the final validator, correcting any ML Kit errors.
- **No Crashes:** The `ImageProxy` memory leak and double-close crash are fixed.
- **Reliable Backend:** Firebase updates are properly awaited, and network errors are handled gracefully without leaving the user in a broken state.
