# Fitness App

- A work in progress.

1. Download the code
2. Open in Android Studio
3. If you have a Fitbit account, you can enter your details. Alternatively you can enter as a new user.
4. Once logged in, user the naviation bar on the top left to scroll through login, user stats, and comparison fragment

- A test account login: user: test; password: test
- Fitbit user info login: Client ID, Client Secret, and Refresh Token are needed to continually generate Refresh Tokens to view data. This removes the 8 hour limit of Access Token. The data is stored in Android sharedPreferences. Ideally it will be stored with Android Keystore in later versions.
