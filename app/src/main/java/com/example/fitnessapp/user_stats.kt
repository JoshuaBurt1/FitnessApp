package com.example.fitnessapp

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.fitnessapp.UserSession.currentUserName
import com.example.fitnessapp.UserSession.isUserLoggedIn
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

/*
TO ADD:
* 1. Average daily steps should not be editable
* 2. Graph intervals should be modifiable and/or scrollable to view earlier and later dates
* 3. Swipe left and right for:
val restingHR: List<Pair<Int,String>>, //List of tuples [restingHeartRate,dateTime]
val calories: List<Pair<String, Int>>, // List of tuples [dateTime, calories]
val cardioScore: List<Pair<String, String>>, // List of tuples [dateTime, vo2Max]
val heartRate: List<Pair<String, Int>> //List of tuples [dateTime, heartRate]
 */

class UserStats : Fragment() {
    private lateinit var firestore: FirebaseFirestore

    private lateinit var lineChart: LineChart

    private lateinit var editStepsDate: EditText
    private lateinit var editStepsNumber: EditText
    private lateinit var ageEditText: EditText
    private lateinit var heightEditText: EditText
    private lateinit var weightEditText: EditText
    private lateinit var averageDailyStepsEditText: EditText
    private lateinit var submitChangesButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_stats, container, false)
        // Initialize Firestore
        activity?.let { activity ->
            FirebaseApp.initializeApp(activity)
            firestore = FirebaseFirestore.getInstance()
        }

        lineChart = view.findViewById(R.id.lineChart)

        editStepsDate = view.findViewById(R.id.editStepsDate)
        editStepsNumber = view.findViewById(R.id.editStepsNumber)
        ageEditText = view.findViewById(R.id.ageEditText)
        heightEditText = view.findViewById(R.id.heightEditText)
        weightEditText = view.findViewById(R.id.weightEditText)
        averageDailyStepsEditText = view.findViewById(R.id.averageDailyStepsEditText)
        submitChangesButton = view.findViewById(R.id.submitChangesButton)

        // Only show button if isUserLoggedIn = true
        if (isUserLoggedIn) {
            submitChangesButton.visibility = View.VISIBLE
        } else {
            submitChangesButton.visibility = View.GONE
        }
        submitChangesButton.setOnClickListener {
            submitChanges()
        }
        fetchStepsData()

        return view
    }

    //only fetch data if user is logged in
    private fun fetchStepsData() {
        val currentUserName = UserSession.currentUserName
        if (currentUserName.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch data from Firestore
        firestore.collection("users")
            .whereEqualTo("displayName", currentUserName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(requireContext(), "No user data found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // *** Get the first document (ideally this is a UNIQUE document) ***
                val document = querySnapshot.documents.first()
                val userData = document.data
                Log.d("FirestoreData", "User Data: $userData")

                // Parse user data
                val age = userData?.get("age") as? Double ?: 0.0
                val height = userData?.get("height") as? Double ?: 0.0
                val weight = userData?.get("weight") as? Double ?: 0.0
                val averageDailySteps = userData?.get("averageDailySteps") as? Double ?: 0.0

                // Populate table
                populateUserDataTable(age, height, weight, averageDailySteps)

                val stepsJson = userData?.get("steps") as? String

                if (stepsJson != null) {
                    // Parse the JSON into a list
                    val stepsList = parseStepsJson(stepsJson)
                    val userName = userData["displayName"] as? String ?: "Unknown User"

                    // Plot the data
                    plotChart(stepsList, userName)
                } else {
                    Log.e("FirestoreError", "Steps data is missing or null")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "Error getting document: ", exception)
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateUserDataTable(age: Double, height: Double, weight: Double, averageDailySteps: Double) {
        // Populate table layout
        val ageTextView = view?.findViewById<TextView>(R.id.ageValueTextView)
        val heightTextView = view?.findViewById<TextView>(R.id.heightValueTextView)
        val weightTextView = view?.findViewById<TextView>(R.id.weightValueTextView)
        val averageDailyStepsTextView = view?.findViewById<TextView>(R.id.averageDailyStepsValueTextView)

        // Set the user data to the TextViews
        ageTextView?.text = "${age}"
        heightTextView?.text = "${height} cm"
        weightTextView?.text = "${weight} kg"
        averageDailyStepsTextView?.text = "${averageDailySteps} steps"

        // Show the TableLayout
        val userDataTable = view?.findViewById<TableLayout>(R.id.userDataTable)
        userDataTable?.visibility = View.VISIBLE
    }



    private fun parseStepsJson(stepsJson: String): List<Pair<String, Int>> {
        val gson = Gson()
        val responseMap: Map<String, Any> =
            gson.fromJson(stepsJson, object : TypeToken<Map<String, Any>>() {}.type)

        // Extract the 'activities-tracker-steps' array from the map
        val stepsArray = responseMap["activities-tracker-steps"] as? List<Map<String, Any>>

        // Check if stepsArray is null
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

    private fun plotChart(data: List<Pair<String, Int>>, userName: String) {
        val descriptionTextView = view?.findViewById<TextView>(R.id.descriptionTextViewUser)
        descriptionTextView?.text = "Steps Data for $userName"

        // Sort the data based on the date (earliest to latest)
        val sortedData = data.sortedBy { parseDate(it.first) }

        // Convert the sorted data to Entry objects for plotting
        val entries = sortedData.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second.toFloat())
        }

        val dataSet = LineDataSet(entries, userName)

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        dataSet.setColor(android.graphics.Color.BLUE)
        dataSet.valueTextColor = android.graphics.Color.BLACK
        dataSet.valueTextSize = 12f

        val xAxisLabels = sortedData.map { it.first }
        Log.d("PlotChart", "X-Axis Labels: $xAxisLabels")
        val xAxis = lineChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(xAxisLabels)
        xAxis.granularity = 1f
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.invalidate()
    }

    // Helper function to parse the date string to a Date object
    private fun parseDate(dateString: String): Date {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.parse(dateString) ?: Date() // Default to current date if parsing fails
    }


    private fun submitChanges() {
        // Get the values from the EditTexts
        val stepsDateText = editStepsDate.text.toString().trim()
        val stepsNumberText = editStepsNumber.text.toString().trim()
        val ageText = ageEditText.text.toString().trim()
        val heightText = heightEditText.text.toString().trim()
        val weightText = weightEditText.text.toString().trim()
        val averageDailyStepsText = averageDailyStepsEditText.text.toString().trim()

        // Ensure the stepsDateText is in the format YYYY-MM-DD (if it's not empty)
        if (stepsDateText.isNotEmpty() && !isValidDate(stepsDateText)) {
            Toast.makeText(requireContext(), "Invalid date format. Please use YYYY-MM-DD", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure stepsNumberText is a valid number (if it's not empty)
        val steps = if (stepsNumberText.isNotEmpty()) stepsNumberText.toIntOrNull() else null
        if (steps == null && stepsNumberText.isNotEmpty()) {
            Toast.makeText(requireContext(), "Please enter a valid number of steps", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch the user's data from Firestore
        firestore.collection("users")
            .whereEqualTo("displayName", currentUserName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(requireContext(), "No user data found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val document = querySnapshot.documents.first()
                val userData = document.data
                val stepsJson = userData?.get("steps") as? String
                Log.d("FirestoreData", "STEPCOUNT: $stepsJson") // list<pair(string,int)>

                val stepsList = stepsJson?.let { parseStepsJson(it) }?.toMutableList()
                Log.d("FirestoreData", "STEPCOUNT: $stepsList") // list<pair(string,int)>

                // Only update steps if stepsDateText and stepsNumberText are not empty
                if (stepsList != null && steps != null && stepsDateText.isNotEmpty()) {
                    var found = false
                    for (i in stepsList.indices) {
                        if (stepsList[i].first == stepsDateText) {
                            // Update the step count for the matching date
                            stepsList[i] = Pair(stepsDateText, steps)
                            found = true
                            break
                        }
                    }

                    // If the date was not found, add a new pair
                    if (!found) {
                        stepsList.add(Pair(stepsDateText, steps))
                    }

                    // Log the updated list
                    Log.d("UpdatedStepsList", "STEPCOUNT: $stepsList")

                    // Update Firestore with the new steps data
                    val updatedStepsJson = convertStepsListToJson(stepsList)
                    document.reference.update("steps", updatedStepsJson)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Steps updated successfully!", Toast.LENGTH_SHORT).show()
                            plotChart(stepsList, currentUserName)
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), "Failed to update steps", Toast.LENGTH_SHORT).show()
                        }
                }

            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT).show()
            }

        // Ensure other values are double (if applicable)
        val age = if (ageText.isNotEmpty()) ageText.toDoubleOrNull() else null
        val height = if (heightText.isNotEmpty()) heightText.toDoubleOrNull() else null
        val weight = if (weightText.isNotEmpty()) weightText.toDoubleOrNull() else null
        val averageDailySteps = if (averageDailyStepsText.isNotEmpty()) averageDailyStepsText.toDoubleOrNull() else null



        val currentUserName = UserSession.currentUserName
        if (currentUserName.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        // Prepare user data updates (for profile)
        val updates = mutableMapOf<String, Any>()
        // Keep the current values if only some are updated
        age?.let { updates["age"] = it }
        height?.let { updates["height"] = it }
        weight?.let { updates["weight"] = it }
        averageDailySteps?.let { updates["averageDailySteps"] = it }

        // Do not update if no changes
        if (updates.isEmpty() && (steps == null || stepsDateText.isEmpty())) {
            Toast.makeText(requireContext(), "No changes to submit", Toast.LENGTH_SHORT).show()
            return
        }

        // Update the user data in Firestore
        firestore.collection("users")
            .whereEqualTo("displayName", currentUserName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(requireContext(), "No user data found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Update user profile data first
                val document = querySnapshot.documents.first()

                // Update user profile data (age, height, weight, etc.)
                document.reference.update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "User profile updated successfully!", Toast.LENGTH_SHORT).show()

                        // Update the TextViews immediately
                        age?.let { view?.findViewById<TextView>(R.id.ageValueTextView)?.text = "$it" }
                        height?.let { view?.findViewById<TextView>(R.id.heightValueTextView)?.text = "$it cm" }
                        weight?.let { view?.findViewById<TextView>(R.id.weightValueTextView)?.text = "$it kg" }
                        averageDailySteps?.let { view?.findViewById<TextView>(R.id.averageDailyStepsValueTextView)?.text = "$it steps" }
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to update user profile", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT).show()
            }

    }


    // Is date in format (YYYY-MM-DD)
    private fun isValidDate(date: String): Boolean {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            LocalDate.parse(date, formatter)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }

    // Create a list of maps with dateTime and value keys
    private fun convertStepsListToJson(stepsList: MutableList<Pair<String, Int>>): String {
        val stepsJsonList = stepsList.map {
            mapOf(
                "dateTime" to it.first,
                "value" to it.second.toString()
            )
        }
        val jsonMap = mapOf("activities-tracker-steps" to stepsJsonList)
        return Gson().toJson(jsonMap)
    }
}

