package com.example.fitnessapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson

class Game : Fragment() {
    private lateinit var lineChart: LineChart  // Declare the lateinit variable

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_game, container, false)

        // Initialize lineChart using findViewById
        lineChart = view.findViewById(R.id.lineChart)
        fetchHeartData()

        return view
    }

    private fun fetchHeartData() {
        // Simulate a response
        val jsonResponse = """{
            "activities-heart-intraday": {
                "dataset": [
                    {"time": "12:05:00", "value": 84},
                    {"time": "12:10:00", "value": 80},
                    {"time": "12:35:00", "value": 82},
                    {"time": "12:45:00", "value": 76}
                ]
            }
        }"""

        val gson = Gson()
        val responseMap = gson.fromJson(jsonResponse, Map::class.java)

        // Extract the 'dataset' from the response
        val dataset = (responseMap["activities-heart-intraday"] as Map<*, *>)["dataset"] as List<Map<String, Any>>

        // Convert the dataset to a list of Pair<String, Int> (time-value pairs)
        val timeValuePairs = dataset.map {
            it["time"] as String to (it["value"] as Double).toInt()
        }

        // Now timeValuePairs contains a list of time-value pairs for heart rate
        // You can log the heart rate values
        timeValuePairs.forEach { pair ->
            Log.d("HeartData", "Time: ${pair.first}, Value: ${pair.second}")
        }

        // Plot the chart with this data
        plotChart(timeValuePairs)
    }

    private fun plotChart(data: List<Pair<String, Int>>) {
        // Convert the time-value pairs to Entry objects for plotting
        val entries = data.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second.toFloat())  // Convert index to float for X axis
        }

        val dataSet = LineDataSet(entries, "Heart Rate")
        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // Additional customization (optional)
        dataSet.setColor(android.graphics.Color.RED)  // Line color
        dataSet.valueTextColor = android.graphics.Color.BLACK  // Value text color
        dataSet.valueTextSize = 12f  // Text size for the values

        lineChart.invalidate() // Refresh the chart to render the data
    }

}
