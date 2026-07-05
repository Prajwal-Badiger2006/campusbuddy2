# Real-Time Features Implementation Guide

This guide outlines how to upgrade the CampusBuddy app from one-time fetches to fully real-time updates using Firebase Firestore and Kotlin Flows.

## The Core Concept
Currently, the app uses `.get().await()` to fetch data once. To make it real-time, you must replace these calls with Firebase's `.addSnapshotListener()` and wrap them in a Kotlin `callbackFlow`. This allows the UI to constantly listen for database changes.

---

## 1. Updating `FirebaseService.kt`
You need to add new functions that return a `Flow` instead of a one-time `List`.

```kotlin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Add this to FirebaseService.kt
fun getMessagesFlow(conversationId: Long): Flow<List<Message>> = callbackFlow {
    val subscription = messageRef
        .whereEqualTo("conversationId", conversationId)
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val messages = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }
                trySend(messages).isSuccess
            }
        }

    // Unsubscribe when the Flow is cancelled (e.g., user leaves the screen)
    awaitClose { subscription.remove() }
}
```

## 2. Updating `CampusBuddyRepository.kt`
Expose the new Flow from the repository so the UI can observe it.

```kotlin
import kotlinx.coroutines.flow.Flow

// Add this to CampusBuddyRepository.kt
fun getMessagesFlow(conversationId: Long): Flow<List<Message>> {
    return firebase.getMessagesFlow(conversationId)
}
```

## 3. Updating `ChatScreen.kt`
Change the Compose UI to collect the Flow as state. This means whenever a new message is added to Firebase, the `messages` list will instantly update and trigger a UI recomposition.

```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatScreen(
    conversationId: Long,
    repository: CampusBuddyRepository,
    // ...
) {
    // Collect the flow as state. It will automatically update in real-time.
    val messages by repository.getMessagesFlow(conversationId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Remove the old manual fetch from LaunchedEffect
    LaunchedEffect(conversationId) {
        val user = repository.getCurrentFirebaseUser() ?: return@LaunchedEffect
        // ... (Keep conversation fetching and markMessagesAsRead logic)
        // REMOVE: repository.getMessages(conversationId).onSuccess { ... }
    }
    
    // ... Rest of the UI remains the same
}
```

## 4. Applying this Pattern to other features
You can repeat this exact pattern to implement all other missing real-time features:
- **Notifications:** Create a `getNotificationsFlow(userId: String)` to instantly show new alerts.
- **Matches:** Create a `getUserMatchesFlow(userId: String)` so the UI updates as soon as someone accepts a request.
- **Online Presence:** You can use Firebase Realtime Database (better suited for presence than Firestore) to track `isOnline` and `lastSeen` fields, and observe them in the chat screen.
