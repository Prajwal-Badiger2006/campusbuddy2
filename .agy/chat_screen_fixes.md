# Chat Screen Fixes

Based on your report, there are two common issues occurring in your `ChatScreen.kt`: the keyboard overlapping the text input area, and UI/timing delays. 

Here is how to fix both issues without changing the overall logic of your app.

## 1. Fix the Keyboard Overlap Issue
In Jetpack Compose, when the keyboard appears, it can cover the input field if the layout isn't explicitly told to pad for the Input Method Editor (IME).

**How to fix:**
In `ChatScreen.kt`, find the root `Column` inside your `Scaffold`. It currently looks like this:
```kotlin
    } { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Messages
```

Add `.imePadding()` to the modifier so it pushes the layout up when the keyboard appears:
```kotlin
    } { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding() // <--- Add this line!
        ) {
            // Messages
```
*(Also, ensure your `MainActivity.kt` calls `WindowCompat.setDecorFitsSystemWindows(window, false)` in `onCreate` if you are using edge-to-edge design).*

## 2. Fix the Message Timing Delay (UI Lag)
You mentioned the message timing is getting delayed. The main culprit for this is your `formatMessageTime` function at the bottom of `ChatScreen.kt`:
```kotlin
fun formatMessageTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
```
**Why it's lagging:** Creating a new `SimpleDateFormat` object is incredibly heavy on performance. Right now, it creates a new one for *every single message* on *every single frame* that recomposes. This severely lags the keyboard and delays the UI.

**How to fix:**
Move the `SimpleDateFormat` outside the function so it is only created once. Replace your function with this:

```kotlin
// Define this at the top level, outside of any functions or classes
private val timeFormatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())

fun formatMessageTime(timestamp: Long): String {
    // If you are using Firebase Server Timestamps, the timestamp might be 0 until it syncs
    if (timestamp <= 0L) {
        return "Sending..." 
    }
    return timeFormatter.format(java.util.Date(timestamp))
}
```

### 3. (Optional) Auto-scroll when keyboard opens
If you want the chat to snap to the latest message as soon as the keyboard opens, you can add this `LaunchedEffect` inside `ChatScreen` alongside your other LaunchedEffects:

```kotlin
    // Scroll to bottom when keyboard appears
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
```
*(Note: This requires `import androidx.compose.foundation.layout.WindowInsets` and `import androidx.compose.foundation.layout.isImeVisible`)*
