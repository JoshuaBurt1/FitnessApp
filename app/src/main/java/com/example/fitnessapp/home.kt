package com.example.fitnessapp

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
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

/**
 * Home fragment
 */class Home : Fragment() {
    private lateinit var homeName: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize Firebase and set logging (ensure it's safe to access context)
        activity?.let { activity ->
            FirebaseApp.initializeApp(activity)
            FirebaseFirestore.setLoggingEnabled(true)
        }

        val navigationView = requireActivity().findViewById<NavigationView>(R.id.nav_view)
        val textViewName = navigationView.getHeaderView(0).findViewById<TextView>(R.id.name)
        val currentName = textViewName.text
        if (!TextUtils.isEmpty(currentName)) {
            homeName.setText(currentName)
        }

        // Fetch the access token asynchronously using lifecycleScope tied to the view lifecycle
        viewLifecycleOwner.lifecycleScope.launch {
            val accessToken = refreshAccessToken()
            if (accessToken != null) {
                fetchFitbitData()
            } else {
                Log.e("FitbitAPI", "Failed to refresh access token")
                Toast.makeText(requireContext(), "Error refreshing token", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }

    private fun updateNameInNavigationView(newName: String) {
        val navigationView = requireActivity().findViewById<NavigationView>(R.id.nav_view)
        val textViewName = navigationView.getHeaderView(0).findViewById<TextView>(R.id.name)
        textViewName.text = newName
    }

    private fun hideKeyboard(context: Context, view: View) {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private suspend fun fetchFitbitApiData(url: String): String {
        val accessToken = refreshAccessToken()
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
                val userName = user["displayName"] as String
                val age = user["age"] as Double
                val gender = user["gender"] as String
                val height = user["height"] as Double
                val weight = user["weight"] as Double

                val userData = hashMapOf(
                    "displayName" to userName,
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

                val db = Firebase.firestore
                db.collection("users")
                    .add(userData)
                    .addOnSuccessListener { documentReference ->
                        Log.d(TAG, "Document added with ID: ${documentReference.id}")
                        Toast.makeText(requireContext(), "Data posted to Firebase!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Error adding document", e)
                        Toast.makeText(requireContext(), "Error posting data", Toast.LENGTH_SHORT).show()
                    }

            } catch (e: Exception) {
                Log.e("FitbitAPI", "Error fetching data: ${e.message}")
                Toast.makeText(requireContext(), "Error fetching data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun refreshAccessToken(): String? {
        val refreshToken = getRefreshToken()
        if (refreshToken == null) {
            Log.e("FitbitAPI", "No refresh token found")
            return null
        }

        val clientId = "23PS6K"
        val clientSecret = "6439f3b4bacfc6bfa202f56920a31f84"
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

                    saveRefreshToken(newRefreshToken)
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
    // Save the refresh token to SharedPreferences
    private fun saveRefreshToken(refreshToken: String) {
        val sharedPreferences = requireContext().getSharedPreferences("fitbit_preferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("refresh_token", refreshToken)
        editor.apply()
    }

    // Retrieve the refresh token from SharedPreferences
    private fun getRefreshToken(): String? {
        val sharedPreferences = requireContext().getSharedPreferences("fitbit_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("refresh_token", null)  // Returns null if no token is found
    }
}
