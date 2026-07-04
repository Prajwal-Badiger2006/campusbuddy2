# Signup Loading Error Investigation

Based on your report that the "Create Account" button gets stuck in a continuous loading state without advancing, I have investigated the code in `SignupScreen.kt`, `CampusBuddyRepository.kt`, and `FirebaseService.kt`.

Here is the exact cause of the infinite loading error:

### 1. The Firestore `.await()` Hang Bug (The Root Cause)
When you click "Create Account", the app runs this sequence:
1. `firebase.signupWithEmail(email, password)` (Authenticates the user)
2. `firebase.createUserProfile(profile)` (Saves the user data to Firestore)

In `FirebaseService.kt`, the `createUserProfile` function uses:
```kotlin
db.collection("userProfiles").document(profile.id).set(profile).await()
```
**The Error:** In the Kotlin Coroutines Firebase SDK, calling `.await()` on a Firestore write operation will **suspend indefinitely (hang forever)** if the Firestore Database cannot be reached. It does not throw an error or time out on its own. 

This usually happens for two reasons:
* **Missing Firestore Database:** You have enabled Authentication in the Firebase Console, but you have not actually clicked **"Create Database"** in the Firestore Database section of the Firebase Console. The app is trying to write to a database that doesn't exist yet, causing it to hang forever.
* **Network Drop:** The device has enough internet to complete the Auth step, but drops connection before the Firestore step. Firestore's offline persistence queues the write and waits forever for the internet to return.

Because it hangs forever, the `try/catch` block is never triggered, `Result.failure` is never returned, and `isLoading` is never set to `false`.

### 2. Missing Timeout Safety Net
**The Architectural Error:** The app currently has no safeguards against Firebase hanging. To prevent the infinite loading spinner in the future, the Firestore write should be wrapped in a Kotlin `withTimeout(10000)` block so that if it takes longer than 10 seconds, it throws a TimeoutException, stops the loading spinner, and shows an error to the user.

### 3. Minor Form Error (Year is not validated)
In `validateForm()`, every field is checked except for the `year` variable. If the user doesn't select a year, the app will still attempt to create an account with a blank year.

---
**Status:** I have only identified the errors as requested and have not changed any code. The most likely immediate fix on your end is to check your Firebase Console and ensure the **Firestore Database** is created and initialized. Let me know if you want me to add the timeout code to fix the app's error handling!
