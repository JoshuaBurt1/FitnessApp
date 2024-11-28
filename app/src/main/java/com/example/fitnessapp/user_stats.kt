package com.example.fitnessapp

import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.fitnessapp.UserSession.isUserLoggedIn
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.text.ParseException
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/*
TO ADD:
* 1. Average daily steps should not be editable
* 2. Python code recommendation (k-means: cardioScore, calories, heartrate, activeZone, steps -> returns recommendation); Flask *****
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
    private lateinit var deleteMessage: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_user_stats, container, false)
        FirebaseApp.initializeApp(requireActivity())
        firestore = FirebaseFirestore.getInstance()

        lineChart = view.findViewById(R.id.lineChart)
        editStepsDate = view.findViewById(R.id.editStepsDate)
        editStepsNumber = view.findViewById(R.id.editStepsNumber)
        ageEditText = view.findViewById(R.id.ageEditText)
        heightEditText = view.findViewById(R.id.heightEditText)
        weightEditText = view.findViewById(R.id.weightEditText)
        averageDailyStepsEditText = view.findViewById(R.id.averageDailyStepsEditText)
        submitChangesButton = view.findViewById(R.id.submitChangesButton)
        deleteMessage= view.findViewById(R.id.deleteMessage)

        submitChangesButton.visibility = if (isUserLoggedIn) View.VISIBLE else View.GONE
        deleteMessage.visibility = if (isUserLoggedIn) View.VISIBLE else View.GONE

        submitChangesButton.setOnClickListener { submitChanges() }
        fetchUserData()

        return view
    }

    //GET request to firestore
    private fun fetchUserData() {
        val currentUserName = UserSession.currentUserName
        firestore.collection("users")
            .whereEqualTo("displayName", currentUserName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                querySnapshot.documents.first().data?.let { userData ->
                    val (age, height, weight, averageDailySteps) = extractUserData(userData)
                    populateUserDataTable(age, height, weight, averageDailySteps)

                    val stepsList = userData["steps"]?.toString()?.let { parseStepsJson(it) }
                    stepsList?.let {
                        plotChart(it.sortedBy { LocalDate.parse(it.first) }, userData["displayName"] as? String ?: "Unknown User")
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT).show()
            }
    }

    //passes values from firestore to datatable during GET request
    private fun extractUserData(userData: Map<String, Any>): UserData {
        val age = (userData["age"] as? Double) ?: 0.0
        val height = (userData["height"] as? Double) ?: 0.0
        val weight = (userData["weight"] as? Double) ?: 0.0
        val averageDailySteps = (userData["averageDailySteps"] as? Double) ?: 0.0
        return UserData(age, height, weight, averageDailySteps)
    }

    //populates age, weight, height, average steps to datatable during GET request
    private fun populateUserDataTable(age: Double, height: Double, weight: Double, averageDailySteps: Double) {
        view?.findViewById<TextView>(R.id.ageValueTextView)?.text = "$age"
        view?.findViewById<TextView>(R.id.heightValueTextView)?.text = "$height cm"
        view?.findViewById<TextView>(R.id.weightValueTextView)?.text = "$weight kg"
        view?.findViewById<TextView>(R.id.averageDailyStepsValueTextView)?.text = "$averageDailySteps steps"
        view?.findViewById<TableLayout>(R.id.userDataTable)?.visibility = View.VISIBLE
    }

    //parse JSON from firestore request
    private fun parseStepsJson(stepsJson: String): List<Pair<String, Int>> {
        val gson = Gson()
        val responseMap: Map<String, Any> = gson.fromJson(stepsJson, object : TypeToken<Map<String, Any>>() {}.type)
        val stepsArray = responseMap["activities-tracker-steps"] as? List<Map<String, Any>> ?: return emptyList()
        return stepsArray.map {
            val date = it["dateTime"] as? String ?: "Unknown Date"
            val steps = (it["value"] as? String)?.toIntOrNull() ?: 0
            Pair(date, steps)
        }
    }

    //plots chart initially and whenever an update is made
    private fun plotChart(data: List<Pair<String, Int>>, userName: String) {
        val entries = data.map { (dateString, steps) ->
            val date = LocalDate.parse(dateString)
            Entry(date.toEpochDay().toFloat(), steps.toFloat())
        }

        val lineDataSet = LineDataSet(entries, userName).apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            setDrawCircles(true)
            setDrawValues(false)
        }

        val lineData = LineData(lineDataSet)
        lineChart.setData(lineData)
        lineChart.invalidate()  // Refresh chart
        configureChart()

        //listener for clicks on linechart to obtain coordinates
        lineChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                e?.let {
                    val dateEpochDay = e.x
                    val steps = e.y.toInt()
                    val date = LocalDate.ofEpochDay(dateEpochDay.toLong()).toString()
                    editStepsDate.setText(date)
                    editStepsNumber.setText(steps.toString())
                }
            }
            override fun onNothingSelected() {
                return
            }
        })
    }

    //configures the x-axis to be divided equally among earliest and latest date
    private fun configureChart() {
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
    }

    //on submit button is pressed-> send update requests to firestore
    private fun submitChanges() {
        val stepsDateText = editStepsDate.text.toString().trim()
        val stepsNumberText = editStepsNumber.text.toString().trim()
        val ageText = ageEditText.text.toString().trim()
        val heightText = heightEditText.text.toString().trim()
        val weightText = weightEditText.text.toString().trim()
        val averageDailyStepsText = averageDailyStepsEditText.text.toString().trim()

        val (steps, valid) = validateInputs(stepsDateText, stepsNumberText)
        if (!valid) return

        val currentUserName = UserSession.currentUserName

        val updates = mutableMapOf<String, Any>().apply {
            ageText.toDoubleOrNull()?.let { put("age", it) }
            heightText.toDoubleOrNull()?.let { put("height", it) }
            weightText.toDoubleOrNull()?.let { put("weight", it) }
            averageDailyStepsText.toDoubleOrNull()?.let { put("averageDailySteps", it) }
        }

        if (updates.isNotEmpty()) updateUserProfile(currentUserName, updates)
        if (steps != null) updateStepsData(currentUserName, stepsDateText, steps)
    }

    //input validation
    private fun validateInputs(stepsDateText: String, stepsNumberText: String): Pair<Int?, Boolean> {
        if (stepsDateText.isNotEmpty() && !isValidDate(stepsDateText)) {
            Toast.makeText(requireContext(), "Invalid date format. Please use YYYY-MM-DD", Toast.LENGTH_SHORT).show()
            return null to false
        }
        if (stepsDateText.isNotEmpty() && isDateInFuture(stepsDateText)) {
            Toast.makeText(requireContext(), "Date cannot be in the future", Toast.LENGTH_SHORT).show()
            return null to false
        }
        val steps = stepsNumberText.toIntOrNull()
        if (steps == null && stepsNumberText.isNotEmpty()) {
            Toast.makeText(requireContext(), "Please enter a valid number of steps", Toast.LENGTH_SHORT).show()
            return null to false
        }
        if (steps != null && steps > 1000000) {
            Toast.makeText(requireContext(), "Please enter a valid number of steps (less than or equal to 1 million)", Toast.LENGTH_SHORT).show()
            return null to false
        }
        return steps to true
    }

    //height, weight, age, average steps changes
    private fun updateUserProfile(currentUserName: String, updates: Map<String, Any>) {
        firestore.collection("users")
            .whereEqualTo("displayName", currentUserName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val documentRef = querySnapshot.documents.firstOrNull()?.reference ?: return@addOnSuccessListener
                documentRef.update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                        fetchUserData()
                    }
            }
    }

    //Steps value changes
    private fun updateStepsData(currentUserName: String, stepsDateText: String, steps: Int) {
        firestore.collection("users")
            .whereEqualTo("displayName", currentUserName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val documentRef = querySnapshot.documents.firstOrNull()?.reference ?: return@addOnSuccessListener
                querySnapshot.documents.first().data?.let { userData ->
                    val stepsList = parseStepsJson(userData["steps"]?.toString() ?: "")
                    val updatedStepsList = stepsList.toMutableList().apply {
                        // DELETE: if date selected and steps = -1
                        if (stepsDateText.isNotEmpty() && steps == -1) {
                            removeIf { it.first == stepsDateText }
                        } else {
                            // UPDATE ONE
                            val existingEntry = find { it.first == stepsDateText }
                            if (existingEntry != null) {
                                set(indexOf(existingEntry), existingEntry.copy(second = steps))
                            } else {
                                // POST
                                add(Pair(stepsDateText, steps))
                            }
                        }
                    }.sortedBy { LocalDate.parse(it.first) }

                    // Convert updated list to JSON and update Firestore
                    val updatedStepsJson = convertStepsListToJson(updatedStepsList)
                    documentRef.update("steps", updatedStepsJson)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Steps updated", Toast.LENGTH_SHORT).show()
                            plotChart(updatedStepsList, currentUserName)
                        }
                }
            }
    }

    //Convert to JSON for updating Firebase
    private fun convertStepsListToJson(stepsList: List<Pair<String, Int>>): String {
        val gson = Gson()
        return gson.toJson(mapOf("activities-tracker-steps" to stepsList.map { mapOf("dateTime" to it.first, "value" to it.second.toString()) }))
    }

    private fun isValidDate(dateString: String): Boolean {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateString) != null
        } catch (e: ParseException) {
            false
        }
    }

    private fun isDateInFuture(dateString: String): Boolean {
        val currentDate = LocalDate.now()
        val enteredDate = LocalDate.parse(dateString)
        return enteredDate.isAfter(currentDate)
    }

    data class UserData(val age: Double, val height: Double, val weight: Double, val averageDailySteps: Double)
}
