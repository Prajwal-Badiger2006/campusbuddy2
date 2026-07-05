# Verification Badge: Bugs & Solutions

This document outlines the bugs identified in the Verification Badge flow during video and code analysis, along with their solutions.

## 1. Missing Input Validation (The "Empty Fields" Bug)
*   **The Bug:** At the "Review Your Details" screen, users can click "Confirm & Save" even if critical fields (like Registration Number and Email Address) are entirely empty. The system accepts this incomplete data and incorrectly grants the Verified status.
*   **The Solution:** Add input validation logic in `ScanIdScreen.kt`. You should disable the "Confirm & Save" button (or show an error toast) if essential fields such as `fullName`, `registrationNumber`, or `collegeName` are blank.

## 2. Firebase Property Mapping (The "Invisible Badge" Bug)
*   **The Bug:** After a successful scan, the "Verification Pending" banner remains permanently on the Profile screen, and the blue checkmark doesn't appear anywhere in the app.
*   **Root Cause:** In `ScanIdScreen.kt`, the app updates the Firebase document using `"isVerifiedStudent" to true`. However, the `UserProfile` data class relies on default Java Bean conventions, meaning it looks for a field named `"verifiedStudent"` when reading from Firestore. Since the names don't match, the app always assumes the user is unverified (`false`).
*   **The Solution:** Add the `@field:PropertyName` annotation to the `isVerifiedStudent` property in `Models.kt` to force an exact match:
```kotlin
    @field:PropertyName("isVerifiedStudent")
    val isVerifiedStudent: Boolean = false,
```

## 3. Persistent Snackbar Bug
*   **The Bug:** The "Verification Successful! Badge Unlocked." snackbar remains stuck on the screen even when navigating between tabs (e.g., from Profile to Home and back).
*   **The Solution:** Ensure that the success flag stored in `UserPreferences` is cleared immediately after the snackbar is shown. Add `userPreferences.clearVerificationSuccess()` in the `LaunchedEffect` block in `ProfileScreen.kt` right after triggering `snackbarHostState.showSnackbar()`.

## 4. Routing Bug (Home Screen Pop-up Navigation)
*   **The Bug:** If a user starts the ID scan by clicking "Proceed" on the new Home screen pop-up, finishing or skipping the scan incorrectly routes them into the Onboarding carousel instead of returning them to the Home screen.
*   **Root Cause:** The `ScanIdRoute` only accepts a `fromProfile` boolean. If it's false, the app assumes the user is in the middle of the initial sign-up flow and navigates to `OnboardingRoute`.
*   **The Solution:** Update the `ScanIdRoute` in your navigation graph to accept a more specific `source` parameter (e.g., `"onboarding"`, `"home"`, `"profile"`). Then, update the `onScanComplete` callback in `NavGraph.kt` so that if `source == "home"`, the user is returned to the Home screen instead of Onboarding.
