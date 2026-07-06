# Bug Report & Solutions (scr1.mp4)

Based on the analysis of the screen recording `scr1.mp4`, two distinct bugs were identified in the user interface and data mapping. Below is the detailed breakdown and the proposed solutions for both.

---

## Bug 1: The "Split Personality" Chat Bug (Different Chat Histories)

### The Issue
* **What happens:** Clicking the "Chat" button from the **Matches** screen opens a chat room with history A (e.g., messages like `"hennyao"` and `"chal"`). However, navigating to the **Chats** tab and clicking the exact same user opens a completely different chat room with history B (e.g., messages like `"hi"` and `"ji"`).
* **Root Cause:** The Firebase database contains duplicate conversation documents for the exact same pair of users. 
  * `ChatsScreen.kt` deduplicates by taking the **first** conversation it finds (`if (partnerId in seenPartners) false`).
  * `MatchesScreen.kt` deduplicates by saving them to a map (`conversations = conversations + (partnerId to conv.id)`), which overwrites the map and takes the **last** conversation it finds.
  * As a result, the two screens route the user to two different Conversation IDs.

### The Solution
1. **Clean up Database (Test Data):** Manually delete the duplicate conversation from your Firebase Firestore to clean up the bad test data.
2. **Sync the Deduplication Logic:** Ensure both screens use the exact same logic. Update `MatchesScreen.kt` to also take the first conversation it finds, matching `ChatsScreen.kt`:
   ```kotlin
   // In MatchesScreen.kt
   repository.getUserConversations(user.uid).onSuccess { convList ->
       convList.forEach { conv ->
           val partnerId = conv.memberIds.find { it != user.uid }
           // Only add it if we haven't seen this partner yet (takes the first one)
           if (partnerId != null && !conversations.containsKey(partnerId)) {
               conversations = conversations + (partnerId to conv.id)
           }
       }
   }
   ```
3. **Repository Refactor (Optional but Recommended):** Move the deduplication logic directly into `CampusBuddyRepository.kt` so that `getUserConversations` always returns a clean, deduplicated list, preventing this issue app-wide.

---

## Bug 2: The "No Matches Yet" UI Flicker

### The Issue
* **What happens:** When the user taps "View Matches", the screen briefly flashes the "No Matches Yet" empty state (with the broken heart icon). A split second later, the UI updates and the actual match appears.
* **Root Cause:** The `MatchesScreen.kt` uses a real-time Flow to read matches:
  ```kotlin
  val allMatches by repository.getMatchesFlow(currentUserId).collectAsStateWithLifecycle(initialValue = emptyList())
  ```
  Because the `initialValue` is an empty list, the `LaunchedEffect(matches)` block triggers immediately and instantly sets `isLoading = false`. The UI sees `isLoading == false` and `matches.isEmpty() == true`, so it draws the empty state. A few milliseconds later, Firebase returns the real data, the list populates, and the UI re-draws correctly.

### The Solution
You need to prevent `isLoading` from turning false before the initial network request completes.

**Fix Option A (Add a small buffer delay):**
Add a small delay before assuming an empty list is the final result.
```kotlin
LaunchedEffect(matches) {
    // ... load partners and conversations ...
    
    // Give Firebase a tiny window to return the real list before dropping the loading skeleton
    if (matches.isEmpty()) {
        kotlinx.coroutines.delay(500) 
    }
    isLoading = false
}
```

**Fix Option B (Better State Management):**
Instead of returning a raw `List<Match>` from your Flow, return a sealed class state (e.g., `UiState.Loading`, `UiState.Success(List)`, `UiState.Empty`). This way, the initial state is strictly `Loading` rather than an artificially empty list, completely eliminating the flicker without arbitrary delays.
