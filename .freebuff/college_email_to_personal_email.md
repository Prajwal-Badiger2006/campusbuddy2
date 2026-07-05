# CampusBuddy: College Email → Personal Email Migration

## Overview

Replaced all "college email" references with "personal email" / "email" across the app. The app now treats the user's email as a general personal email rather than requiring a college/institutional email for signup.

## Files Changed

### 1. Data Model — `app/src/main/java/com/example/campusbuddy/data/models/Models.kt`
- Renamed `UserProfile.collegeEmail` → `email`
- Added `@field:PropertyName("collegeEmail")` annotation to maintain backward compatibility with existing Firestore documents that use the `collegeEmail` field name
- Added `import com.google.firebase.firestore.PropertyName`

### 2. Repository — `app/src/main/java/com/example/campusbuddy/data/repository/CampusBuddyRepository.kt`
- Updated signup function: `collegeEmail = email` → `email = email` (matches new property name)

### 3. Auth Screens

#### SignupScreen.kt
- Label: `"College Email"` → `"Email Address"`
- Placeholder: `"you@college.edu"` → `"you@email.com"`
- Hint text: `"Email must include @ and a domain (e.g., .edu)"` → `"Email must include @ and a domain (e.g., .com)"`

#### LoginScreen.kt
- Label: `"College Email"` → `"Email Address"`
- Placeholder: `"you@college.edu"` → `"you@email.com"`

#### ForgotPasswordScreen.kt
- Description: `"Enter your college email and we'll send you a reset link"` → `"Enter your email address and we'll send you a reset link"`
- Label: `"College Email"` → `"Email Address"`

### 4. OCR System

#### StudentIdData.kt
- Renamed field: `collegeEmail` → `email`
- Updated `hasAnyData` getter to reference `email` instead of `collegeEmail`

#### OcrProcessor.kt
- Updated `parseBackSide()` to use `existingData.email` / `StudentIdData.email` instead of `collegeEmail`
- Updated `extractEmail()` documentation comments to use generic "email" language (not "college email")
- **Domain validation change**: Removed Gmail, Yahoo, Hotmail, and Outlook from the rejection list in `extractEmail()`. Now only blocks Rediffmail and Protonmail. Since users can sign up with any personal email, the OCR scanner no longer requires an institutional domain.

#### ScanIdScreen.kt
- Updated data reference: `currentData.collegeEmail` → `currentData.email`
- Label: `"College Email"` → `"Email Address"`
- Firestore update key `"collegeEmail"` preserved with a comment noting backward compatibility

## Firebase Compatibility

- **Firestore field name**: The underlying Firestore field in `userProfiles` documents remains `collegeEmail` (via `@field:PropertyName("collegeEmail")`). All existing user data remains readable.
- **Update operations**: The `ScanIdScreen.kt` continues to use `"collegeEmail"` as the Firestore update key.
- **Read operations**: `doc.toObject(UserProfile::class.java)` will correctly map the `collegeEmail` field from Firestore to the `email` property via the `@PropertyName` annotation.
- **Write operations**: `document.set(profile)` will write to the `collegeEmail` Firestore field thanks to the annotation.

## Verification

- All UI labels now consistently show "Email Address" (Signup, Login, Forgot Password, OCR Review)
- No remaining references to `collegeEmail` as a Kotlin property name in any source file
- The only remaining `"collegeEmail"` strings are Firestore field name references (intentional for backward compatibility)
