package com.example.fitnessapp

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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class Game : Fragment() {
    private lateinit var lineChart: LineChart
    private lateinit var firestore: FirebaseFirestore

    private lateinit var ageEditText: EditText
    private lateinit var heightEditText: EditText
    private lateinit var weightEditText: EditText
    private lateinit var averageDailyStepsEditText: EditText
    private lateinit var submitChangesButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_game, container, false)
        // Initialize Firestore
        activity?.let { activity ->
            FirebaseApp.initializeApp(activity)
            firestore = FirebaseFirestore.getInstance()
        }

        lineChart = view.findViewById(R.id.lineChart)

        lineChart = view.findViewById(R.id.lineChart)
        ageEditText = view.findViewById(R.id.ageEditText)
        heightEditText = view.findViewById(R.id.heightEditText)
        weightEditText = view.findViewById(R.id.weightEditText)
        averageDailyStepsEditText = view.findViewById(R.id.averageDailyStepsEditText)
        submitChangesButton = view.findViewById(R.id.submitChangesButton)

        submitChangesButton.setOnClickListener {
            submitChanges()
        }

        fetchStepsData()

        return view
    }

    //only fetch data if user is logged in
    private fun fetchStepsData() {
        if (!UserSession.isUserLoggedIn) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }
        val currentUserName = UserSession.currentUserName

        if (currentUserName.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No logged-in user found", Toast.LENGTH_SHORT).show()
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
        val descriptionTextView = view?.findViewById<TextView>(R.id.descriptionTextView)
        descriptionTextView?.text = "Steps Data for $userName"

        // Convert the time-value pairs to Entry objects for plotting
        val entries = data.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second.toFloat())
        }
        val dataSet = LineDataSet(entries, userName)

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        dataSet.setColor(android.graphics.Color.BLUE)
        dataSet.valueTextColor = android.graphics.Color.BLACK
        dataSet.valueTextSize = 12f
        lineChart.invalidate()  // Refresh the chart to render the data
    }

    private fun submitChanges() {
        // Get the values from the EditTexts
        val ageText = ageEditText.text.toString().trim()
        val heightText = heightEditText.text.toString().trim()
        val weightText = weightEditText.text.toString().trim()
        val averageDailyStepsText = averageDailyStepsEditText.text.toString().trim()

        // Ensure values are double
        val age = if (ageText.isNotEmpty()) ageText.toDoubleOrNull() else null
        val height = if (heightText.isNotEmpty()) heightText.toDoubleOrNull() else null
        val weight = if (weightText.isNotEmpty()) weightText.toDoubleOrNull() else null
        val averageDailySteps = if (averageDailyStepsText.isNotEmpty()) averageDailyStepsText.toDoubleOrNull() else null

        // Check if the user is logged in
        if (!UserSession.isUserLoggedIn) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }
        val currentUserName = UserSession.currentUserName
        if (currentUserName.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No logged-in user found", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mutableMapOf<String, Any>()
        // Keep the current values if only some are updated
        age?.let { updates["age"] = it }
        height?.let { updates["height"] = it }
        weight?.let { updates["weight"] = it }
        averageDailySteps?.let { updates["averageDailySteps"] = it }

        // Do not update if no changes
        if (updates.isEmpty()) {
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
                // *** Update the first document (ideally this is a UNIQUE document) ***
                val document = querySnapshot.documents.first()
                document.reference.update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Changes submitted successfully!", Toast.LENGTH_SHORT).show()

                        // Update the TextViews immediately
                        if (age != null) {
                            view?.findViewById<TextView>(R.id.ageValueTextView)?.text = "$age"
                        }
                        if (height != null) {
                            view?.findViewById<TextView>(R.id.heightValueTextView)?.text = "$height cm"
                        }
                        if (weight != null) {
                            view?.findViewById<TextView>(R.id.weightValueTextView)?.text = "$weight kg"
                        }
                        if (averageDailySteps != null) {
                            view?.findViewById<TextView>(R.id.averageDailyStepsValueTextView)?.text = "$averageDailySteps steps"
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to submit changes", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT).show()
            }
    }
}

