# Verification Badge Flow

## 1. Post-Signup Trigger
- **Condition**: After a new user successfully completes the sign-up and onboarding process (e.g., selecting interests, skills) and lands on the `Home Screen` for the first time.
- **Backend/State Check**: The app checks the user's `UserProfile` document to see if `isVerifiedStudent` is `false`.

## 2. Home Page Pop-up Screen
- **UI Element**: A modal dialog or bottom sheet displayed over the Home screen.
- **Content**: 
  - **Title**: "Get Your Verified Student Badge! 🛡️"
  - **Description**: "Verify your student status using your College ID to build trust and connect with more study partners."
  - **Actions**:
    - **Primary Button (`Proceed`)**: Immediately starts the ID scanning verification process.
    - **Secondary Button (`Do it later`)**: Dismisses the pop-up.

## 3. "Proceed" Flow (Immediate Verification)
- **Navigation**: The app navigates the user to the `ScanIdScreen` (OCR processing).
- **Process**:
  1. User scans the **Front** of their College ID.
  2. User scans the **Back** of their College ID.
  3. The `OcrProcessor` extracts relevant fields (Name, Registration Number, College Name, etc.).
  4. User reviews and confirms the extracted data.
- **Completion**: Upon confirmation, the backend updates the user's profile (`isVerifiedStudent = true`). The app navigates the user back to the Home screen and displays a success message (e.g., "Verification Successful! Badge Unlocked."). Once the verification is completed, a blue-colored verification checkmark (right mark) will be shown beside their name on their profile page and across the app.

## 4. "Do it later" Flow (Deferred Verification)
- **Action**: If the user clicks `Do it later`, the pop-up is dismissed, allowing them to use the app normally as an unverified user.
- **Profile Page Integration**: 
  - A persistent "Action Required" or "To-Do" banner is displayed at the top of the **Profile Page**.
  - **Banner Content**: "Verification Pending: Complete your profile by verifying your student ID to unlock all features."
  - **Action**: A `Verify Now` button on the banner routes the user directly to the `ScanIdScreen`.
- **Dynamic Visibility**: This banner remains visible on the Profile page *only* while `isVerifiedStudent == false`. Once the user completes the scan, the banner automatically disappears and is replaced by the blue-colored verification checkmark beside their name on the profile page.

## 5. Edge Cases & Robustness (Filling the Holes)
To ensure the flow works accurately without bugs, the following logic must be implemented:

- **Preventing Pop-up Spam**: 
  - *Hole*: If the user clicks "Do it later," the pop-up shouldn't reappear every single time they open the app and go to the Home screen.
  - *Fix*: Use local storage (DataStore or SharedPreferences) to save a `hasSeenVerificationPopup` boolean flag. The Home screen pop-up should only trigger if `!isVerifiedStudent && !hasSeenVerificationPopup`.
- **Dynamic Return Navigation**: 
  - *Hole*: After completing the scan, the app needs to know where to send the user back to.
  - *Fix*: Pass a `fromProfile` boolean argument to the `ScanIdScreen` route. 
    - If they started from the Home pop-up (`fromProfile = false`), navigate back to `Home`.
    - If they started from the Profile banner (`fromProfile = true`), navigate back to `Profile`.
- **Graceful Abandonment**: 
  - *Hole*: The user clicks "Proceed" but backs out of the camera screen without scanning.
  - *Fix*: Ensure the back button simply pops the backstack. The user remains unverified, and they can still access the "Verify Now" banner on their Profile page later.
- **Camera Permissions**: 
  - *Hole*: The app crashes if the user clicks "Proceed" but hasn't granted camera access.
  - *Fix*: The `ScanIdScreen` must explicitly check for and request `Manifest.permission.CAMERA` before attempting to open the camera preview.
