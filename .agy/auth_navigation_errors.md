# Auth and Navigation Flow Issues

I have investigated the auth and navigation flow, specifically why the app might not show the Home screen after an account is created. I found a few critical issues in the flow:

## 1. Back Stack Not Clearing Correctly (Navigation Flow)
In `NavGraph.kt`, when you navigate forward from screens like `EmailVerificationRoute`, `ProfileSetupRoute`, and `OnboardingRoute` to `HomeRoute`, the code attempts to clear the back stack using:
```kotlin
popUpTo(SplashRoute) { inclusive = true }
```
**The Issue:** `SplashRoute` is already popped off the stack when the user initially navigates from Splash -> Login. Because `SplashRoute` is no longer in the back stack, this `popUpTo` command fails silently. 
**The Result:** The user successfully navigates to the Home screen, but if they press the system "Back" button, instead of exiting the app, they will be taken back to Onboarding -> Profile Setup -> Email Verification -> Signup -> Login.

## 2. SplashScreen Bypasses Setup (Auth Flow)
In `SplashScreen.kt`, the app checks where to route an existing user:
```kotlin
} else if (profile.fullName.isNotEmpty() && profile.department.isNotEmpty()) {
    onNavigateToHome()
} else {
    onNavigateToProfileSetup()
}
```
**The Issue:** `fullName` and `department` are always populated during the initial `SignupScreen`. 
**The Result:** If a user closes the app during Email Verification or Profile Setup, and then reopens it, the app will see that `fullName` is not empty and will navigate them straight to the `HomeRoute`. They will completely skip Email Verification and Profile Setup. The condition should instead check if they are verified and if their profile setup is complete (e.g., `profile.status == UserStatus.VERIFIED && profile.interests.isNotEmpty()`).

## 3. Email Verification Gets Users Stuck (Auth Flow)
In `EmailVerificationScreen.kt`, the user is asked to enter a 6-digit OTP code to verify their college email.
**The Issue:** The Firebase authentication integration in `CampusBuddyRepository.kt` and `FirebaseService.kt` only uses `createUserWithEmailAndPassword`, which creates the account but **does not send an OTP code to the user's email**. 
**The Result:** A real user will get stuck on this screen waiting for an email that never comes. (The code currently just checks `if (otp.length == 6)`, so typing any 6 numbers would let them bypass it, but a real user wouldn't know this). This is likely why it feels like the app "is not showing the home after an account has created" — the user is permanently stuck waiting for an OTP.

---
**Status:** I have only checked the flow and logged these errors. I have not changed any code. Let me know if you would like me to fix these issues.
