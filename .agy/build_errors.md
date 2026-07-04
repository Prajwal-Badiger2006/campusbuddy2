# Compilation Errors Summary

The app is currently failing to build due to the following compilation errors:

## 1. Kotlin Version Mismatch (Firebase Auth)
*   **Error**: `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.3.0, expected version is 2.1.0.`
*   **File**: `firebase-auth-api`
*   **Impact**: The version of Kotlin used to compile `firebase-auth-api` is newer than the Kotlin version the project is using. This completely breaks the authentication flow.

## 2. Unresolved References (Missing Imports or Incorrect Usage)
Several components, icons, and variables couldn't be found:
*   **`FirebaseService.kt`** (Lines 9 & 22): Unresolved reference `auth`.
*   **`ForgotPasswordScreen.kt`** (Line 80): Unresolved reference `Email`.
*   **`SignupScreen.kt`**:
    *   Line 77: Unresolved reference `Icons.Filled.ArrowBack` (receiver type mismatch).
    *   Line 105: Unresolved reference `Person`.
    *   Line 117: Unresolved reference `Email`.
*   **`ChatScreen.kt`** (Line 74): Unresolved reference `ArrowBack`.
*   **`AppComponents.kt`**:
    *   Line 615: Unresolved reference `Full`.
    *   Line 843: Unresolved reference `Icons.Outlined.ArrowBack`.
*   **`NotificationsScreen.kt`** (Line 114): Unresolved reference `notifScope`.
*   **`PartnerProfileScreen.kt`** (Line 61): Unresolved reference `Icons.Filled.ArrowBack`.
*   **`CreateRequestScreen.kt`**:
    *   Line 138: Unresolved reference `Remove`.
    *   Line 143: Unresolved reference `Add`.

## 3. @Composable Invocation Context Error
*   **`AppComponents.kt`** (Lines 170-190): `@Composable invocations can only happen from the context of a @Composable function`. There is an issue with the parameters being passed to `OutlinedTextField` inside `AppTextField`.
*   **Impact**: Breaks every screen that uses `AppTextField` or `AppPasswordField` (e.g., Login, Signup, Profile Setup, Create Request).

## 4. Experimental Material API Errors
Using Experimental Material APIs without explicitly opting in via `@OptIn(ExperimentalMaterial3Api::class)`.
*   **`ChatScreen.kt`** (Lines 63, 84)
*   **`AppComponents.kt`** (Lines 834, 848)
*   **`PartnerProfileScreen.kt`** (Lines 57, 82)

## 5. Smart Cast Error
*   **`RequestDetailsScreen.kt`** (Line 81): `Smart cast to 'kotlin.String' is impossible, because 'description' is a delegated property.`
*   **Fix Idea**: Assign the delegated property to a local `val` first before checking its type/nullability.
