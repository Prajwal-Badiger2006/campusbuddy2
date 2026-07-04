# UI/UX Improvements Review

This document outlines the UI/UX issues and recommended improvements for the Onboarding and Signup screens.

## 1. Onboarding Screen (`OnboardingScreen.kt`)
* **Fix the Button Layout "Jump":** The `Skip` button is hidden on the final page. Because it sits directly below the `Next` button, when a user reaches the last page, the `Skip` button disappears and causes the `Get Started` button to abruptly drop down. Moving the `Skip` button to the top-right corner will prevent layout shifting.
* **Upgrade the Graphics:** Replace the basic Material icons with custom vector illustrations or Lottie animations to make the app feel high-end and polished.
* **Add Dynamic Page Transitions:** Implement Parallax scrolling or fade/scale animations to add a sense of depth and interactivity as the user swipes.
* **Improve the Indicator Visuals:** Smooth out the indicator animation (e.g., using a "worm" or "stretching" effect) when transitioning between pages.
* **Add Background Gradients / Color Shifts:** Animate the background color to slightly different pastel or theme-aligned gradient tones as the user swipes through the pages.

## 2. Signup Screen (`SignupScreen.kt`)
* **Form Fatigue (Too Many Fields at Once):** The user is hit with a wall of 8 input fields on a single screen, creating cognitive overload. Breaking this into a Multi-Step Wizard (e.g., 2 or 3 smaller screens) will make the process feel much faster and less intimidating.
* **Replace the "Year" Dropdown with Choice Chips:** Using a horizontal row of Choice Chips or a Segmented Button for the 4 year options makes all options immediately visible and selectable with a single tap. *(Note: We recently updated the text fields to use a cleaner `AppDropdownField`, but choice chips are still superior for inputs with few options like Year).*
* **Move from "On-Submit" to "Real-Time" Validation:** Implement inline, real-time validation (e.g., dynamic password checklist, instant email formatting check) rather than only showing errors after the user clicks "Create Account".
* **Group Related Fields Visually:** Use visual grouping (like Cards or Sub-headers with larger spacing between sections) to organize related information (e.g., Personal, Academic, Security).
* **Keyboard Flow and Focus Management:** Use `FocusRequester` for each field and configure `KeyboardActions` so that pressing "Next" on the virtual keyboard smoothly jumps to the exact next field without requiring manual taps.
