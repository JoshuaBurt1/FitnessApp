package com.example.fitnessapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.time.LocalDate
import kotlin.random.Random

/*
TO ADD:
* 1. X-axis for chart
* 2. Swipe left and right for:
val restingHR: List<Pair<Int,String>>, //List of tuples [restingHeartRate,dateTime]
val calories: List<Pair<String, Int>>, // List of tuples [dateTime, calories]
val cardioScore: List<Pair<String, String>>, // List of tuples [dateTime, vo2Max]
val heartRate: List<Pair<String, Int>> //List of tuples [dateTime, heartRate]
 */

class Compare : Fragment() {
    private lateinit var lineChart: LineChart
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_compare, container, false)
        // Initialize Firestore
        activity?.let { activity ->
            FirebaseApp.initializeApp(activity)
            firestore = FirebaseFirestore.getInstance()
        }
        lineChart = view.findViewById(R.id.lineChartAllUsers)
        fetchStepsData()
        return view
    }

    // Parses the JSON from Fitbit
    private fun parseStepsJson(stepsJson: String): List<Pair<String, Int>> {
        val gson = Gson() //parses JSON
        val responseMap: Map<String, Any> = gson.fromJson(stepsJson, object : TypeToken<Map<String, Any>>() {}.type)
        val stepsArray = responseMap["activities-tracker-steps"] as? List<Map<String, Any>> ?: return emptyList()

        return stepsArray.map { step ->
            val date = step["dateTime"] as? String ?: "Unknown Date"
            val steps = (step["value"] as? String)?.toIntOrNull() ?: 0
            date to steps
        }
    }
    //GET request to firestore database for steps data
    private fun fetchStepsData() {
        if (!UserSession.isUserLoggedIn) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("users")
            .get()
            .addOnSuccessListener { querySnapshot ->
                // Step 1: Collect all entries and dates across users
                val allData = querySnapshot.documents
                    .mapNotNull { document ->
                        val userData = document.data ?: return@mapNotNull null
                        val stepsJson = userData["steps"] as? String ?: return@mapNotNull null

                        // Parse and sort the steps data
                        val stepsList = parseStepsJson(stepsJson).sortedBy { LocalDate.parse(it.first) }
                        val userName = userData["displayName"] as? String ?: "Unknown User"

                        userName to stepsList
                    }

                // Step 2: Extract dates and generate datasets
                val allEntriesAndDates = allData.map { (userName, stepsList) ->
                    createUserLineDataSet(userName, stepsList)
                }

                // Step 3: Unzip the entries and dates into separate lists
                val (allEntries, allDatesFormatted) = allEntriesAndDates.unzip()

                plotChart(allEntries, allDatesFormatted)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "Error getting documents: ", exception)
            }
    }

    // Function to create the dataset for each user
    private fun createUserLineDataSet(userName: String, stepsList: List<Pair<String, Int>>): Pair<LineDataSet, List<String>> {
        val entries = stepsList.map { pair ->
            val date = LocalDate.parse(pair.first)
            val adjustedX = date.toEpochDay()
            Entry(adjustedX.toFloat(), pair.second.toFloat())
        }
        val dates = stepsList.map { it.first }

        // Create LineDataSet and return it with the dates
        val lineDataSet = LineDataSet(entries, userName).apply {
            color = randomColor()
            setCircleColor(color)
            valueTextColor = Color.BLACK
            valueTextSize = 12f
        }
        return lineDataSet to dates
    }

    // Plot the chart
    private fun plotChart(allEntries: List<LineDataSet>, allDates: List<List<String>>) {
        val lineData = LineData(allEntries)
        lineChart.data = lineData

        // Flatten the list of dates and format them for the x-axis
        val allDateStrings = allDates.flatten()
        val sortedDateStrings = allDateStrings.distinct().sorted()
        Log.d("PlotChart", "X-Axis LabelsX: $sortedDateStrings")
        val xAxis = lineChart.xAxis
        Log.d("PlotChart", "X-Axis LabelsX: $xAxis")
        //xAxis.valueFormatter = IndexAxisValueFormatter(sortedDateStrings) // Display dateTime on x-axis
        xAxis.granularity = 1f
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.invalidate()
    }

    // Random colour generation for lines
    private fun randomColor(): Int {
        return Color.rgb(
            Random.nextInt(256),  // red
            Random.nextInt(256),  // green
            Random.nextInt(256)   // blue
        )
    }
}
