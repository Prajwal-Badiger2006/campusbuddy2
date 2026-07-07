# Video 2 (v2.mp4) Bug Analysis & Solution

## 🐞 The Bug Observed
In `v2.mp4`, when you type and send multiple "hello" messages, the new messages do not appear above the text box. Instead, they vanish **behind the keyboard and text input field**. 
The list is auto-scrolling, but because the layout does not shrink to accommodate the keyboard, the "bottom" of the chat list is physically rendered underneath the keyboard. When you finally close the keyboard at `00:20`, all the hidden messages suddenly appear.

## 🛠 Why this is happening
In your `MainActivity.kt`, you are calling `enableEdgeToEdge()`. This is great for modern UI, but it means Jetpack Compose is fully responsible for handling window insets (like the keyboard appearing). 
Currently, your `ChatScreen.kt` uses a `Scaffold`, but the content inside the Scaffold doesn't know how much to pad itself at the bottom when the keyboard (IME) slides up.

## ✅ The Solution
You need to tell the root layout of your `ChatScreen` to add padding at the bottom exactly equal to the height of the keyboard when it opens.

**1. Update `ChatScreen.kt`**
Find the `Column` inside your `Scaffold` (around line 110) and chain the `.imePadding()` and `.consumeWindowInsets()` modifiers:

```kotlin
    } { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding) // <-- Add this
                .imePadding()                      // <-- Add this
        ) {
            // Messages list and input field...
```

**2. Ensure `AndroidManifest.xml` is configured correctly**
Open your `app/src/main/AndroidManifest.xml` and make sure your `<activity>` tag for `MainActivity` has this property so Android knows to resize the window for the keyboard:
```xml
android:windowSoftInputMode="adjustResize"
```

By adding `.imePadding()`, the bottom of the `Column` (which holds your text field) will get pushed up by the exact height of the keyboard, and the `LazyColumn` will shrink so the newest messages always stay visible right above the text box!
