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
    private lateinit var lineChart: LineChart  // Declare the lateinit variable
    private lateinit var firestore: FirebaseFirestore  // Declare Firestore instance
    private var userName: String = "Unknown User"  // Class level variable to hold username

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

        activity?.let { activity ->
            FirebaseApp.initializeApp(activity)
            firestore = FirebaseFirestore.getInstance()  // Initialize Firestore
        }

        // Initialize lineChart using findViewById
        lineChart = view.findViewById(R.id.lineChart)

        lineChart = view.findViewById(R.id.lineChart)
        ageEditText = view.findViewById(R.id.ageEditText)
        heightEditText = view.findViewById(R.id.heightEditText)
        weightEditText = view.findViewById(R.id.weightEditText)
        averageDailyStepsEditText = view.findViewById(R.id.averageDailyStepsEditText)
        submitChangesButton = view.findViewById(R.id.submitChangesButton)

        // Set up the submit button listener
        submitChangesButton.setOnClickListener {
            submitChanges()
        }

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

        // Get the currently logged-in user's username (assumed to be stored in UserSession)
        val currentUserName = UserSession.currentUserName

        if (currentUserName.isNullOrEmpty()) {
            // If no user name is available, show an error and return
            Toast.makeText(requireContext(), "No logged-in user found", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch data from Firestore for the logged-in user
        firestore.collection("users")
            .whereEqualTo("displayName", currentUserName) // Use the current logged-in user's display name
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    // No user found in Firestore (shouldn't happen if the user is logged in)
                    Toast.makeText(requireContext(), "No user data found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // We are assuming there's only one document for the current user
                val document = querySnapshot.documents.first()
                val userData = document.data
                Log.d("FirestoreData", "User Data: $userData")

                // Parse the data from Profile:
                val age = userData?.get("age") as? Double ?: 0.0
                val height = userData?.get("height") as? Double ?: 0.0
                val weight = userData?.get("weight") as? Double ?: 0.0
                val averageDailySteps = userData?.get("averageDailySteps") as? Double ?: 0.0

                // Populate the TableLayout with this data
                populateUserDataTable(age, height, weight, averageDailySteps)

                // Check if "steps" data exists in the document
                val stepsJson = userData?.get("steps") as? String

                // If stepsJson is not null, proceed to parse it
                if (stepsJson != null) {
                    // Parse the JSON string into a usable list
                    val stepsList = parseStepsJson(stepsJson)
                    val userName = userData["displayName"] as? String ?: "Unknown User"

                    // Plot the data with the username included as a label
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
        // Access the TableLayout and TextViews to populate them
        val ageTextView = view?.findViewById<TextView>(R.id.ageValueTextView)
        val heightTextView = view?.findViewById<TextView>(R.id.heightValueTextView)
        val weightTextView = view?.findViewById<TextView>(R.id.weightValueTextView)
        val averageDailyStepsTextView = view?.findViewById<TextView>(R.id.averageDailyStepsValueTextView)

        // Set the user data to the TextViews
        ageTextView?.text = "${age}"
        heightTextView?.text = "${height} cm"  // Assuming height is in cm
        weightTextView?.text = "${weight} kg"  // Assuming weight is in kg
        averageDailyStepsTextView?.text = "${averageDailySteps} steps"

        // Show the TableLayout if it's initially hidden
        val userDataTable = view?.findViewById<TableLayout>(R.id.userDataTable)
        userDataTable?.visibility = View.VISIBLE
    }



    private fun parseStepsJson(stepsJson: String): List<Pair<String, Int>> {
        val gson = Gson()
        val responseMap: Map<String, Any> =
            gson.fromJson(stepsJson, object : TypeToken<Map<String, Any>>() {}.type)

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

        // Convert valid values to double or leave as null if blank
        val age = if (ageText.isNotEmpty()) ageText.toDoubleOrNull() else null
        val height = if (heightText.isNotEmpty()) heightText.toDoubleOrNull() else null
        val weight = if (weightText.isNotEmpty()) weightText.toDoubleOrNull() else null
        val averageDailySteps = if (averageDailyStepsText.isNotEmpty()) averageDailyStepsText.toDoubleOrNull() else null

        // Check if the user is logged in
        if (!UserSession.isUserLoggedIn) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        // Get the currently logged-in user's username
        val currentUserName = UserSession.currentUserName
        if (currentUserName.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No logged-in user found", Toast.LENGTH_SHORT).show()
            return
        }

        // Prepare the updates map
        val updates = mutableMapOf<String, Any>()

        // Only update Firestore with non-null values
        age?.let { updates["age"] = it }
        height?.let { updates["height"] = it }
        weight?.let { updates["weight"] = it }
        averageDailySteps?.let { updates["averageDailySteps"] = it }

        // If there are no updates, don't proceed
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

                // Update the first document found
                val document = querySnapshot.documents.first()
                document.reference.update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Changes submitted successfully!", Toast.LENGTH_SHORT).show()

                        // Update the TextViews immediately
                        if (age != null) {
                            view?.findViewById<TextView>(R.id.ageValueTextView)?.text = "$age"
                        }
                        if (height != null) {
                            view?.findViewById<TextView>(R.id.heightValueTextView)?.text = "$height cm"  // Assuming height is in cm
                        }
                        if (weight != null) {
                            view?.findViewById<TextView>(R.id.weightValueTextView)?.text = "$weight kg"  // Assuming weight is in kg
                        }
                        if (averageDailySteps != null) {
                            view?.findViewById<TextView>(R.id.averageDailyStepsValueTextView)?.text = "$averageDailySteps steps"
                        }
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(requireContext(), "Failed to submit changes", Toast.LENGTH_SHORT).show()
                        Log.e("FirestoreError", "Error updating document", exception)
                    }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT).show()
                Log.e("FirestoreError", "Error getting document: ", exception)
            }
    }
}

