package com.example.fitnessapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import android.graphics.Color
import android.widget.Toast
import kotlin.random.Random


class Highscores : Fragment() {
    private lateinit var lineChart: LineChart  // Declare the lateinit variable
    private lateinit var firestore: FirebaseFirestore  // Declare Firestore instance

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_highscores, container, false)

        activity?.let { activity ->
            FirebaseApp.initializeApp(activity)
            firestore = FirebaseFirestore.getInstance()  // Initialize Firestore
        }

        // Initialize lineChart using findViewById
        lineChart = view.findViewById(R.id.lineChartAllUsers)

        // Fetch steps data for all users
        fetchStepsData()

        return view
    }

    private fun fetchStepsData() {
        if (!UserSession.isUserLoggedIn) {
            // If the user is not logged in, show a message and do not proceed with fetching data
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }
        // Fetch data from Firestore
        firestore.collection("users")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val allEntries = mutableListOf<LineDataSet>() // This will hold multiple LineDataSet objects

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

                        // Create a data set for this user and add it to the list
                        val userName = userData["displayName"] as? String ?: "Unknown User"
                        val dataSet = createUserLineDataSet(userName, stepsList)
                        allEntries.add(dataSet)
                    } else {
                        Log.e("FirestoreError", "Steps data is missing or null")
                    }
                }

                // Plot all the user data sets on the chart
                plotChart(allEntries)
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

    private fun createUserLineDataSet(userName: String, stepsList: List<Pair<String, Int>>): LineDataSet {
        // Convert the time-value pairs to Entry objects for plotting
        val entries = stepsList.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second.toFloat())  // Convert index to float for X axis
        }

        // Create a LineDataSet for this user
        val dataSet = LineDataSet(entries, userName)
        dataSet.color = randomColor()  // Correctly call the randomColor function
        dataSet.valueTextColor = Color.BLACK  // Value text color
        dataSet.valueTextSize = 12f  // Text size for the values
        return dataSet
    }

    private fun plotChart(allEntries: List<LineDataSet>) {
        // Create a LineData object with all user data sets
        val lineData = LineData(allEntries)

        // Set the data on the chart
        lineChart.data = lineData

        // Additional customization for the chart
        lineChart.description.isEnabled = false  // Disable the description
        lineChart.legend.isEnabled = true  // Enable the legend for user names
        lineChart.xAxis.setPosition(XAxis.XAxisPosition.BOTTOM)  // Position X axis labels at the bottom
        lineChart.invalidate() // Refresh the chart to render the data
    }

    fun randomColor(): Int {
        // Generate a random RGB color using random values for red, green, and blue
        return Color.rgb(
            Random.nextInt(256),  // Random red value (0-255)
            Random.nextInt(256),  // Random green value (0-255)
            Random.nextInt(256)   // Random blue value (0-255)
        )
    }
}
