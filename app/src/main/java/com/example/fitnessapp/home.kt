package com.example.fitnessapp

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.google.common.reflect.TypeToken
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class Home : Fragment() {

    private lateinit var usernameInputEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var regularUserLogin: Button

    private lateinit var clientIdEditText: EditText
    private lateinit var clientSecretEditText: EditText
    private lateinit var refreshTokenEditText: EditText
    private lateinit var fitbitUserLogin: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize Firebase and logging
        activity?.let { activity ->
            FirebaseApp.initializeApp(activity)
            FirebaseFirestore.setLoggingEnabled(true)
        }

        // *** Regular User login process: ***
        usernameInputEditText = view.findViewById(R.id.usernameInput)
        passwordEditText = view.findViewById(R.id.passwordInput)
        // Initialize the button
        regularUserLogin = view.findViewById(R.id.regularUserLogin)
        regularUserLogin.setOnClickListener {
            if (validateUserForm()) {
                val userNameRegular = usernameInputEditText.text.toString()
                val passwordRegular = passwordEditText.text.toString()

                val userData = hashMapOf(
                    "displayName" to userNameRegular,
                    "encodedId" to passwordRegular
                )

                // Query Firestore to check if a document with the displayName already exists
                val db = Firebase.firestore
                val query = db.collection("users").whereEqualTo("displayName", userNameRegular)

                query.get()
                    .addOnSuccessListener { querySnapshot ->
                        if (querySnapshot.isEmpty) {
                            // No document found, create a new one
                            Log.d(
                                "Firestore",
                                "No existing user found with displayName: $userNameRegular. Creating new entry."
                            )
                            UserSession.currentUserName = userNameRegular

                            db.collection("users")
                                .add(userData)
                                .addOnSuccessListener { documentReference ->
                                    Log.d(TAG, "Document added with ID: ${documentReference.id}")
                                    Toast.makeText(requireContext(), "Data posted to Firebase!", Toast.LENGTH_SHORT).show()
                                    enableNavigation()
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Error adding document", e)
                                    Toast.makeText(requireContext(), "Error posting data", Toast.LENGTH_SHORT).show()
                                    enableNavigation()
                                }
                        } else {
                            // Document found with matching displayName, update the existing one
                            val documentId = querySnapshot.documents.first().id
                            Log.d(
                                "Firestore",
                                "Existing user found with displayName: $userNameRegular. Updating document ID: $documentId."
                            )
                            UserSession.currentUserName = userNameRegular
                        }
                        UserSession.isUserLoggedIn = true
                        Toast.makeText(requireContext(), "Logged in: $userNameRegular", Toast.LENGTH_SHORT).show()

                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error checking for existing user", e)
                        Toast.makeText(requireContext(), "Error checking for existing user", Toast.LENGTH_SHORT).show()
                        enableNavigation()
                        UserSession.isUserLoggedIn = false

                    }
            }
        }

        // *** Fitbit User login process: ***
        clientIdEditText = view.findViewById(R.id.clientID)
        clientSecretEditText = view.findViewById(R.id.clientSecret)
        refreshTokenEditText = view.findViewById(R.id.refreshToken)
        // Initialize the button
        fitbitUserLogin = view.findViewById(R.id.fitbitUserLogin)
        // Load the stored values from SharedPreferences
        loadStoredCredentials()

        fitbitUserLogin.setOnClickListener {
            // Get the values from the EditText fields
            val clientId = clientIdEditText.text.toString()
            val clientSecret = clientSecretEditText.text.toString()
            val refreshToken = refreshTokenEditText.text.toString()

            if (clientId.isNotEmpty() && clientSecret.isNotEmpty() && refreshToken.isNotEmpty()) {
                // Save them to SharedPreferences
                saveCredentialsToSharedPreferences(clientId, clientSecret, refreshToken)

                // Start the process to fetch datais
                viewLifecycleOwner.lifecycleScope.launch {
                    val accessToken = refreshAccessToken(refreshToken)
                    if (accessToken != null) {
                        // User is logged in, fetch Fitbit data
                        UserSession.isUserLoggedIn = true
                        fetchFitbitData()
                    } else {
                        // Failed to refresh the token
                        Log.e("FitbitAPI", "Failed to refresh access token")
                        Toast.makeText(requireContext(), "Error refreshing token", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Prompt user if any credentials are missing
                Toast.makeText(requireContext(), "Please enter valid credentials", Toast.LENGTH_SHORT).show()
            }
            Toast.makeText(requireContext(), "Loading data...", Toast.LENGTH_SHORT).show()

        }
        return view
    }

    private fun saveCredentialsToSharedPreferences(clientId: String, clientSecret: String, refreshToken: String) {
        val sharedPreferences = requireContext().getSharedPreferences("fitbit_preferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("client_id", clientId)
        editor.putString("client_secret", clientSecret)
        editor.putString("refresh_token", refreshToken)
        editor.apply()
    }

    private fun loadStoredCredentials() {
        val sharedPreferences = requireContext().getSharedPreferences("fitbit_preferences", Context.MODE_PRIVATE)
        val storedClientId = sharedPreferences.getString("client_id", "")
        val storedClientSecret = sharedPreferences.getString("client_secret", "")
        val storedRefreshToken = sharedPreferences.getString("refresh_token", "")

        // Check if any value is stored and set input type as textPassword or plain text
        if (!storedClientId.isNullOrEmpty()) {
            clientIdEditText.setText(storedClientId)  // Set stored value
            // Only apply password behavior when the user interacts with the field
            setPasswordBehavior(clientIdEditText)
        }
        if (!storedClientSecret.isNullOrEmpty()) {
            clientSecretEditText.setText(storedClientSecret)  // Set stored value
            setPasswordBehavior(clientSecretEditText)
        }
        if (!storedRefreshToken.isNullOrEmpty()) {
            refreshTokenEditText.setText(storedRefreshToken)  // Set stored value
            setPasswordBehavior(refreshTokenEditText)
        }
    }

    private suspend fun refreshAccessToken(refreshToken: String): String? {
        val clientId = getStoredClientId()
        val clientSecret = getStoredClientSecret()

        if (clientId == null || clientSecret == null || refreshToken.isNullOrEmpty()) {
            Log.e("FitbitAPI", "Client ID, Client Secret or Refresh Token is missing.")
            return null
        }

        val url = "https://api.fitbit.com/oauth2/token"
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()

        val credentials = "$clientId:$clientSecret"
        val basicAuth = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .header("Authorization", basicAuth)
            .build()

        val client = OkHttpClient()
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonResponse = JSONObject(responseBody)
                    val newAccessToken = jsonResponse.optString("access_token")
                    val newRefreshToken = jsonResponse.optString("refresh_token")

                    if (newAccessToken.isNullOrEmpty()) {
                        Log.e("FitbitAPI", "Access token is empty or null.")
                        return@withContext null
                    }

                    // Save the new tokens in SharedPreferences
                    saveCredentialsToSharedPreferences(clientId, clientSecret, newRefreshToken)
                    return@withContext newAccessToken
                } else {
                    Log.e("FitbitAPI", "Failed to refresh token: ${response.code}")
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e("FitbitAPI", "Error refreshing token: ${e.message}")
                return@withContext null
            }
        }
    }

    private fun getStoredClientId(): String? {
        val sharedPreferences = requireContext().getSharedPreferences("fitbit_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("client_id", null)
    }

    private fun getStoredClientSecret(): String? {
        val sharedPreferences = requireContext().getSharedPreferences("fitbit_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("client_secret", null)
    }

    private fun getStoredRefreshToken(): String? {
        val sharedPreferences = requireContext().getSharedPreferences("fitbit_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("refresh_token", null)
    }

    private suspend fun fetchFitbitApiData(url: String): String {
        val refreshToken = getStoredRefreshToken()
        if (refreshToken.isNullOrEmpty()) {
            throw Exception("Error: No valid refresh token found")
        }

        val accessToken = refreshAccessToken(refreshToken)
        if (accessToken == null) {
            throw Exception("Error: No valid access token found")
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("FitbitAPI", "Response: $responseBody")
                    return@withContext responseBody ?: "No data"
                } else {
                    throw Exception("Error fetching data: ${response.code}")
                }
            } catch (e: Exception) {
                throw Exception("Error: ${e.message}")
            }
        }
    }


    private fun fetchFitbitData() {
        disableNavigation()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profileJson = fetchFitbitApiData("https://api.fitbit.com/1/user/-/profile.json")
                val dailyStepsJson = fetchFitbitApiData("https://api.fitbit.com/1/user/-/activities/tracker/steps/date/2024-11-07/today.json")
                val cardioScoreJson = fetchFitbitApiData("https://api.fitbit.com/1/user/-/cardioscore/date/2024-11-07/2024-11-24.json")
                val caloriesJson = fetchFitbitApiData("https://api.fitbit.com/1/user/-/activities/tracker/calories/date/2024-11-07/today.json")
                val activeZoneJson = fetchFitbitApiData("https://api.fitbit.com/1/user/-/activities/active-zone-minutes/date/2024-11-07/today.json")
                val heartRateJson = fetchFitbitApiData("https://api.fitbit.com/1/user/-/activities/heart/date/2024-11-07/1d/5min.json")

                // Parse and log the response
                Log.d("FitbitAPI", "Profile: $profileJson")
                Log.d("FitbitAPI", "Steps: $dailyStepsJson")
                Log.d("FitbitAPI", "Cardio Score: $cardioScoreJson")
                Log.d("FitbitAPI", "Calories: $caloriesJson")
                Log.d("FitbitAPI", "Active Zone: $activeZoneJson")
                Log.d("FitbitAPI", "Heart Rate: $heartRateJson")

                val gson = Gson()
                val fitbitResponse: Map<String, Any> = gson.fromJson(profileJson, object : TypeToken<Map<String, Any>>() {}.type)

                // Handle parsed data
                val user = fitbitResponse["user"] as Map<String, Any>
                val fitbitUserName = user["displayName"] as String
                val password = user["encodedId"] as String
                val age = user["age"] as Double
                val gender = user["gender"] as String
                val height = user["height"] as Double
                val weight = user["weight"] as Double

                val userData = hashMapOf(
                    "displayName" to fitbitUserName,
                    "encodedId" to password,
                    "age" to age,
                    "gender" to gender,
                    "height" to height,
                    "weight" to weight,
                    "steps" to dailyStepsJson,
                    "cardioScore" to cardioScoreJson,
                    "calories" to caloriesJson,
                    "activeZone" to activeZoneJson,
                    "heartRate" to heartRateJson
                )

                // Query Firestore to check if a document with the displayName already exists
                val db = Firebase.firestore
                val query = db.collection("users").whereEqualTo("displayName", fitbitUserName)

                query.get()
                    .addOnSuccessListener { querySnapshot ->
                        if (querySnapshot.isEmpty) {
                            // No document found, create a new one
                            Log.d("Firestore", "No existing user found with displayName: $fitbitUserName. Creating new entry.")
                            UserSession.currentUserName = fitbitUserName
                            db.collection("users")
                                .add(userData)
                                .addOnSuccessListener { documentReference ->
                                    Log.d(TAG, "Document added with ID: ${documentReference.id}")
                                    Toast.makeText(requireContext(), "Data posted to Firebase!", Toast.LENGTH_SHORT).show()
                                    Toast.makeText(requireContext(), "Logged in: $fitbitUserName", Toast.LENGTH_SHORT).show()
                                    enableNavigation()
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Error adding document", e)
                                    Toast.makeText(requireContext(), "Error posting data", Toast.LENGTH_SHORT).show()
                                    enableNavigation()
                                }
                        } else {
                            // Document found with matching displayName, update the existing one
                            val documentId = querySnapshot.documents.first().id
                            Log.d("Firestore", "Existing user found with displayName: $fitbitUserName. Updating document ID: $documentId.")
                            UserSession.currentUserName = fitbitUserName
                            db.collection("users").document(documentId)
                                .set(userData)
                                .addOnSuccessListener {
                                    Log.d(TAG, "Document updated with ID: $documentId")
                                    Toast.makeText(requireContext(), "Data updated in Firebase!", Toast.LENGTH_SHORT).show()
                                    Toast.makeText(requireContext(), "Logged in: $fitbitUserName", Toast.LENGTH_SHORT).show()
                                    enableNavigation()
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Error updating document", e)
                                    Toast.makeText(requireContext(), "Error updating data", Toast.LENGTH_SHORT).show()
                                    enableNavigation()
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error checking for existing user", e)
                        Toast.makeText(requireContext(), "Error checking for existing user", Toast.LENGTH_SHORT).show()
                        enableNavigation()
                    }

            } catch (e: Exception) {
                Log.e("FitbitAPI", "Error fetching data: ${e.message}")
                Toast.makeText(requireContext(), "Error fetching data: ${e.message}", Toast.LENGTH_SHORT).show()
                enableNavigation()
            }
        }
    }

    private fun disableNavigation() {
        val navigationView = requireActivity().findViewById<NavigationView>(R.id.nav_view)
        navigationView.menu.findItem(R.id.nav_home)?.isEnabled = false
        navigationView.menu.findItem(R.id.nav_game)?.isEnabled = false
        navigationView.menu.findItem(R.id.nav_high_score)?.isEnabled = false
    }

    private fun enableNavigation() {
        val navigationView = requireActivity().findViewById<NavigationView>(R.id.nav_view)
        navigationView.menu.findItem(R.id.nav_home)?.isEnabled = true
        navigationView.menu.findItem(R.id.nav_game)?.isEnabled = true
        navigationView.menu.findItem(R.id.nav_high_score)?.isEnabled = true
    }
    private fun setPasswordBehavior(editText: EditText) {
        // Apply password behavior (hide text)
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        editText.setSelection(editText.text.length)  // Move cursor to end of the text

        // Optionally, show the password when focused (or if toggled by user action)
        editText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Allow text to be visible if focused (or leave it as password)
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
    }

    // Validate user form fields (username, password)
    private fun validateUserForm(): Boolean {
        val username = usernameInputEditText.text.toString()
        val password = passwordEditText.text.toString()

        // Check if both fields are not empty
        if (username.isEmpty()) {
            Toast.makeText(requireContext(), "Enter a username", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.isEmpty()) {
            Toast.makeText(requireContext(), "Enter a password", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
}