package com.example.fitnessapp

import android.content.ContentValues.TAG
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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/*
TO ADD:
* 0. Load: A. sample (refresh for different sample), B. team, C. friends list, D. search bar for individual:
clicking on name individual links to user_stats (like duolingo)
* 1. Swipe left and right for:
val calories: List<Pair<String, Int>>, // List of tuples [dateTime, calories]
val cardioScore: List<Pair<String, String>>, // List of tuples [dateTime, vo2Max]
val heartRate: List<Pair<String, Int>> //List of tuples [dateTime, heartRate]
2. Summary of each graph:
* Highest average
* Peak
* Minimum, etc.
* What can k-means do that regular statistics cant
3. Add team -> show a specific subset of users to compare to.
4. sample value amounts should be viewable onclick like in user_stats.kt
5. hash and cache current data to prevent multiple GETS and long response time? Lag when loading user_stats & compare.
 */

class Compare : Fragment() {
    private lateinit var lineChart: LineChart
    private lateinit var firestore: FirebaseFirestore
    private lateinit var descriptionTextViewAll: TextView


    private val allLineDataSets = mutableListOf<LineDataSet>()

    private val statsSections = listOf(
        { fetchStepsData() },   // Index 0 -> Show basic fragment version
        { fetchHRData() }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_compare, container, false)
        FirebaseApp.initializeApp(requireContext())
        firestore = FirebaseFirestore.getInstance()
        lineChart = view.findViewById(R.id.lineChartAllUsers)
        descriptionTextViewAll = view.findViewById(R.id.descriptionTextViewAll)

        if(UserSession.activeSectionIndex==0) {
            //for now this
            fetchStepsData()
        }
        if(UserSession.activeSectionIndex==1) {
            fetchStepsData()
        }
        if(UserSession.activeSectionIndex==2) {
            fetchHRData()
        }

        // Handle right and left button clicks; changes UserSession.activeSectionIndex value
        view.findViewById<Button>(R.id.rotateLeftButton).setOnClickListener {
            rotateView(-1)
        }
        view.findViewById<Button>(R.id.rotateRightButton).setOnClickListener {
            rotateView(1)
        }

        return view
    }

    // Function to move views left or right
    private fun rotateView(direction: Int) {
        val currentSectionIndex = UserSession.activeSectionIndex
        UserSession.activeSectionIndex = (currentSectionIndex + direction + statsSections.size) % statsSections.size
        allLineDataSets.clear()
        statsSections[UserSession.activeSectionIndex].invoke()
        Log.d("CURRENTSECTION", UserSession.activeSectionIndex.toString())
    }

    // Parse JSON steps data
    private fun parseStepsJson(stepsJson: String): List<Pair<String, Int>> {
        val type = object : TypeToken<Map<String, List<Map<String, Any>>>>() {}.type
        return Gson().fromJson<Map<String, List<Map<String, Any>>>>(stepsJson, type)
            .getOrDefault("activities-tracker-steps", emptyList())
            .mapNotNull { step ->
                val date = step["dateTime"] as? String ?: return@mapNotNull null
                val steps = (step["value"] as? String)?.toIntOrNull() ?: 0
                date to steps
            }
    }

    // Parse JSON heartRate data
    private fun parseRestingHeartRateJson(restingHeartRateJson: String): List<Pair<String, Int>> {
        val gson = Gson()
        try {
            val jsonElement = gson.fromJson(restingHeartRateJson, JsonElement::class.java)
            val activitiesHeartArray = jsonElement.asJsonObject.getAsJsonArray("activities-heart")
            val resultList = mutableListOf<Pair<String, Int>>()
            for (activity in activitiesHeartArray) {
                val dateTime = activity.asJsonObject.get("dateTime").asString
                val valueObject = activity.asJsonObject.getAsJsonObject("value")
                val restingHeartRate = valueObject?.get("restingHeartRate")?.asInt
                restingHeartRate?.let {
                    resultList.add(Pair(dateTime, it))
                }
            }
            return resultList
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Malformed JSON detected in heartRate data: $restingHeartRateJson", e)
            return emptyList()
        }
    }

    // GET request to Firestore for fetching user data related to heartRate
    private fun fetchHRData() {
        if (!UserSession.isUserLoggedIn) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }
        firestore.collection("users")
            .get()
            .addOnSuccessListener { querySnapshot ->
                querySnapshot.documents.forEach { document ->
                    val userData = document.data ?: return@forEach
                    val hrJson = userData["heartRate"] as? String
                    if (hrJson == null) {
                        return@forEach
                    }
                    val hrList = parseRestingHeartRateJson(hrJson).sortedBy { LocalDate.parse(it.first) }
                    val userName = userData["displayName"] as? String ?: "Unknown User"
                    descriptionTextViewAll.text = "Resting Heart Rate for All Users (sample)"
                    plotChart(hrList, userName)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT).show()
            }
    }

    // GET: steps data from Firestore and process using FP
    private fun fetchStepsData() {
        if (!UserSession.isUserLoggedIn) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }
        firestore.collection("users")
            .get()
            .addOnSuccessListener { querySnapshot ->
                querySnapshot.documents.forEach { document ->
                    val userData = document.data ?: return@forEach
                    val stepsJson = userData["steps"] as? String ?: return@forEach
                    val stepsList = parseStepsJson(stepsJson).sortedBy { LocalDate.parse(it.first) }
                    val userName = userData["displayName"] as? String ?: "Unknown User"
                    descriptionTextViewAll.text = "Daily Steps for All Users (sample)"
                    plotChart(stepsList, userName)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "Error getting documents: ", exception)
            }
    }

    // Plot the chart by mapping steps data to chart entries
    private fun plotChart(data: List<Pair<String, Int>>, userName: String) {
        val entries = data.map { (dateString, steps) ->
            val date = LocalDate.parse(dateString)
            Entry(date.toEpochDay().toFloat(), steps.toFloat())
        }
        val lineDataSet = LineDataSet(entries, userName).apply {
            color = randomColor()
            setCircleColor(color)
        }
        allLineDataSets.add(lineDataSet)
        val lineData = LineData(allLineDataSets as List<ILineDataSet>)
        configureChart(lineData)
    }

    // Configure chart settings such as x-axis labels and formatter
    private fun configureChart(lineData: LineData) {
        lineChart.data = lineData
        val xAxis = lineChart.xAxis
        xAxis.granularity = 5f
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = object : ValueFormatter() {
            private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                val date = Date(value.toLong() * 24 * 60 * 60 * 1000)
                return dateFormat.format(date)
            }
        }
        lineChart.invalidate()
    }

    // Generate a random color for line
    private fun randomColor(): Int = Color.rgb(
        Random.nextInt(256),
        Random.nextInt(256),
        Random.nextInt(256)
    )
}
