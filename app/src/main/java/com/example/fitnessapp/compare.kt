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
import kotlin.random.Random

// Shows a comparison of all users daily step counts in the firestore cloud
class Highscores : Fragment() {
    private lateinit var lineChart: LineChart
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_highscores, container, false)

        //Initialize firestore
        activity?.let { activity ->
            FirebaseApp.initializeApp(activity)
            firestore = FirebaseFirestore.getInstance()
        }

        lineChart = view.findViewById(R.id.lineChartAllUsers)

        fetchStepsData()

        return view
    }

    // Fetch the steps data from firestore, then plot all the users data on the chart
    private fun fetchStepsData() {
        if (!UserSession.isUserLoggedIn) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }
        // Fetch data from Firestore
        firestore.collection("users")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val allEntries = mutableListOf<LineDataSet>()

                for (document in querySnapshot) {
                    val userData = document.data
                    Log.d("FirestoreData", "User Data: $userData")

                    val stepsJson = userData["steps"] as? String

                    if (stepsJson != null) {
                        val stepsList = parseStepsJson(stepsJson)

                        val userName = userData["displayName"] as? String ?: "Unknown User"
                        val dataSet = createUserLineDataSet(userName, stepsList)
                        allEntries.add(dataSet)
                    } else {
                        Log.e("FirestoreError", "Steps data is missing or null")
                    }
                }
                // Plot the chart
                plotChart(allEntries)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "Error getting documents: ", exception)
            }
    }

    // Parses the JSON from Fitbit using Gson() into a List<Map<String, Any>> type which can be plotted
    private fun parseStepsJson(stepsJson: String): List<Pair<String, Int>> {
        val gson = Gson()
        val responseMap: Map<String, Any> = gson.fromJson(stepsJson, object : TypeToken<Map<String, Any>>() {}.type)

        val stepsArray = responseMap["activities-tracker-steps"] as? List<Map<String, Any>>

        if (stepsArray != null) {
            return stepsArray.map {
                val date = it["dateTime"] as? String ?: "Unknown Date"
                val steps = (it["value"] as? String)?.toIntOrNull() ?: 0
                Pair(date, steps)
            }
        } else {
            Log.e("FirestoreError", "Invalid or missing 'activities-tracker-steps' field")
            return emptyList()
        }
    }

    //Create the line chart
    private fun createUserLineDataSet(userName: String, stepsList: List<Pair<String, Int>>): LineDataSet {
        val entries = stepsList.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second.toFloat())
        }
        val dataSet = LineDataSet(entries, userName)
        dataSet.color = randomColor()
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 12f
        return dataSet
    }

    //Plot the chart
    private fun plotChart(allEntries: List<LineDataSet>) {
        val lineData = LineData(allEntries)

        lineChart.data = lineData

        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = true
        lineChart.xAxis.setPosition(XAxis.XAxisPosition.BOTTOM)
        lineChart.invalidate()
    }

    //Generate a random colour for the lines
    fun randomColor(): Int {
        return Color.rgb(
            Random.nextInt(256),  // red
            Random.nextInt(256),  // green
            Random.nextInt(256)   // blue
        )
    }
}
