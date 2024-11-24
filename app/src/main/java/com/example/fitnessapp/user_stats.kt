package com.example.fitnessapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class Game : Fragment() {
    private lateinit var lineChart: LineChart  // Declare the lateinit variable
    private lateinit var firestore: FirebaseFirestore  // Declare Firestore instance

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_game, container, false)

        activity?.let { activity ->
            FirebaseApp.initializeApp(activity)
            firestore = FirebaseFirestore.getInstance()  // Initialize Firestore
        }

        // Initialize lineChart using findViewById
        lineChart = view.findViewById(R.id.lineChart)

        fetchStepsData()

        return view
    }

    private fun fetchStepsData() {
        // Check if the user is logged in
        if (!UserSession.isUserLoggedIn) {
            // If the user is not logged in, show a message and do not proceed with fetching data
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }
        // Fetch data from Firestore
        firestore.collection("users")
            .get()
            .addOnSuccessListener { querySnapshot ->
                for (document in querySnapshot) {
                    // Assuming the document fields are stored as key-value pairs
                    val userData = document.data
                    Log.d("FirestoreData", "User Data: $userData")

                    // Check if "steps" data exists in the document
                    val stepsJson = userData["steps"] as? String

                    // If stepsJson is not null, proceed to parse it
                    if (stepsJson != null) {
                        // Parse the JSON string into a usable list
                        val stepsList = parseStepsJson(stepsJson)

                        // Plot the data
                        plotChart(stepsList)
                    } else {
                        Log.e("FirestoreError", "Steps data is missing or null")
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "Error getting documents: ", exception)
            }
    }


    private fun parseStepsJson(stepsJson: String): List<Pair<String, Int>> {
        val gson = Gson()
        val responseMap: Map<String, Any> = gson.fromJson(stepsJson, object : TypeToken<Map<String, Any>>() {}.type)

        // Extract the 'activities-tracker-steps' array from the map
        val stepsArray = responseMap["activities-tracker-steps"] as? List<Map<String, Any>>

        // Check if stepsArray is null or empty
        if (stepsArray != null) {
            return stepsArray.map {
                val date = it["dateTime"] as? String ?: "Unknown Date"  // Safe cast to String
                val steps = (it["value"] as? String)?.toIntOrNull() ?: 0  // Safe cast to Int
                Pair(date, steps)
            }
        } else {
            Log.e("FirestoreError", "Invalid or missing 'activities-tracker-steps' field")
            return emptyList()
        }
    }

    private fun plotChart(data: List<Pair<String, Int>>) {
        // Convert the time-value pairs to Entry objects for plotting
        val entries = data.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second.toFloat())  // Convert index to float for X axis
        }

        val dataSet = LineDataSet(entries, "Steps")
        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // Additional customization (optional)
        dataSet.setColor(android.graphics.Color.BLUE)  // Line color
        dataSet.valueTextColor = android.graphics.Color.BLACK  // Value text color
        dataSet.valueTextSize = 12f  // Text size for the values

        lineChart.invalidate() // Refresh the chart to render the data
    }
}
