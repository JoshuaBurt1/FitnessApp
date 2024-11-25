# Fitness App

- A work in progress.

1. Download the code
2. Open in Android Studio
3. Build and press play (green triangle). Alternatively, download to your phone through USB.
4. If you have a Fitbit account, you can enter your details. Alternatively, you can enter as a new user.
5. Once logged in, use the navigation bar on the top left to scroll through login, user stats, and comparison fragment

- A test account login: user: test; password: test
- Fitbit user info login: Client ID, Client Secret, and Refresh Token are needed to continually generate Refresh Tokens to view data. This removes the 8 hour limit of Access Token. The data is stored in Android sharedPreferences and not stored in any database. Ideally it will be stored with Android Keystore in later versions.
- If nothing is showing in User stats or Compare fragment immediately, it is probably because the connection to Firestore database timed out. A toast message on the home screen will occur saying  "User logged in." if successful.
Logcat Example:
2024-11-25 08:45:00.433 18685-18745 Firestore    
com.example.fitnessapp               
W  (25.1.1) [OnlineStateTracker]: Could not reach Cloud Firestore backend. Backend didn't respond within 10 seconds. This typically indicates that your device does not have a healthy Internet connection at the moment. The client will operate in offline mode until it is able to successfully connect to the backend.
