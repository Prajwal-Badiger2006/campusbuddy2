# CampusBuddy Bug Fixes Report

This document outlines the two critical logic bugs that were identified and resolved in `CampusBuddyRepository.kt`.

## 1. Streak Calculation Logic Error (Infinite Streak Glitch)

> [!WARNING]
> Without this fix, users could artificially inflate their streaks simply by logging out and logging back in multiple times on the same day.

### The Issue
In the `updateStreak` function, the app evaluated if the user had logged in yesterday to decide whether to increment their streak. However, it failed to check if the user had already logged in **today**. As a result, if a user logged in multiple times on the same day (having logged in the previous day), their streak count would incorrectly increment by 1 on every single login attempt.

### The Fix
Added logic to verify if there's already an activity log for today. If so, the streak calculation is skipped, preventing the streak from incrementing indefinitely.

```diff
    private suspend fun updateStreak(userId: String) {
+       val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
+       val todayActivity = firebase.getActivityForDate(userId, today)
+       // If already logged in today, streak is already updated
+       if (todayActivity.isNotEmpty()) {
+           return
+       }
+
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis() - 86400000))
        val yesterdayActivity = firebase.getActivityForDate(userId, yesterday)
```

## 2. N+1 Query Performance Bug

> [!TIP]
> This optimization vastly reduces network load and speeds up the reliability score calculation.

### The Issue
In the `recalculateReliability` function, there was a loop `.count { ... }` that checked a user's completed interactions across all their matches. Inside this loop, a network call `firebase.getUserConversations(userId)` was being executed to fetch the user's conversations. If a user had 50 matches, this would unnecessarily execute 50 sequential network requests, severely impacting application performance and Firebase usage.

### The Fix
Moved the network call outside of the loop. The list of conversations is now fetched just once and reused for all matches during the calculation.

```diff
    private suspend fun recalculateReliability(userId: String) {
        val matches = firebase.getUserMatches(userId)
        val totalInteractions = matches.size
        if (totalInteractions == 0) return

+       val conversations = firebase.getUserConversations(userId)
        val completedInteractions = matches.count { match ->
-           val conversations = firebase.getUserConversations(userId)
            conversations.any { conv ->
                conv.memberIds.contains(userId) && conv.memberIds.contains(
                    if (match.user1Id == userId) match.user2Id else match.user1Id
                )
            }
        }
```
