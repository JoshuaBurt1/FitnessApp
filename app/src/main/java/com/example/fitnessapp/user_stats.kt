package com.example.fitnessapp

import android.content.ContentValues.TAG
import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
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
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.navigation.NavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.JsonElement
import java.text.ParseException
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/*
TO ADD:
* Prevent navigation during gets and posts to prevent crash
* 0. Loading animation before any data appears
* 1. Average daily steps should not be editable
* 2. Python code recommendation (k-means: heartrate, steps, sleepscore*** -> returns recommendation); Flask *****
* 3. Swipe left and right for:
- basic stats (add: medications, replacements, injury, adverse reactions to treatment + points)
- strength and endurance: weights and distance times (track and swim)
- nutrition
val restingHR: List<Pair<Int,String>>, //List of tuples [restingHeartRate,dateTime]
val calories: List<Pair<String, Int>>, // List of tuples [dateTime, calories]
val cardioScore: List<Pair<String, String>>, // List of tuples [dateTime, vo2Max]
 */


class UserStats : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var lineChart: LineChart

    private lateinit var descriptionTextViewUser: TextView
    private lateinit var editStepsDate: EditText
    private lateinit var editStepsNumber: EditText
    private lateinit var editHRDateTime: EditText
    private lateinit var editHRNumber: EditText
    private lateinit var ageEditText: EditText
    private lateinit var heightEditText: EditText
    private lateinit var weightEditText: EditText
    private lateinit var averageDailyStepsEditText: EditText
    private lateinit var submitChangesButton: Button
    private lateinit var rotateLeftButton: Button
    private lateinit var rotateRightButton: Button
    private lateinit var deleteMessage: TextView

    private lateinit var basicStatsRows: List<TableRow>
    private lateinit var stepsStatsRows: List<TableRow>
    private lateinit var heartRateStatsRows: List<TableRow>

    private val statsSections = listOf(
        { showBasicStats() },   // Index 0 -> Show basic fragment version
        { showStepsStats() },   // Index 1 -> Show steps fragment version
        { showHeartRateStats() } // Index 2 -> Show heart rate fragment version
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_stats, container, false)
        FirebaseApp.initializeApp(requireActivity())
        firestore = FirebaseFirestore.getInstance()

        descriptionTextViewUser = view.findViewById(R.id.descriptionTextViewUser)
        lineChart = view.findViewById(R.id.lineChart)
        editStepsDate = view.findViewById(R.id.editStepsDate)
        editStepsNumber = view.findViewById(R.id.editStepsNumber)
        editHRDateTime = view.findViewById(R.id.editHRDateTime)
        editHRNumber = view.findViewById(R.id.editHRNumber)
        ageEditText = view.findViewById(R.id.ageEditText)
        heightEditText = view.findViewById(R.id.heightEditText)
        weightEditText = view.findViewById(R.id.weightEditText)
        averageDailyStepsEditText = view.findViewById(R.id.averageDailyStepsEditText)
        submitChangesButton = view.findViewById(R.id.submitChangesButton)
        rotateLeftButton = view.findViewById(R.id.rotateLeftButton)
        rotateRightButton = view.findViewById(R.id.rotateRightButton)
        deleteMessage = view.findViewById(R.id.deleteMessage)

        submitChangesButton.visibility = if (isUserLoggedIn) View.VISIBLE else View.GONE
        rotateLeftButton.visibility = if (isUserLoggedIn) View.VISIBLE else View.GONE
        rotateRightButton.visibility = if (isUserLoggedIn) View.VISIBLE else View.GONE
        deleteMessage.visibility = if (isUserLoggedIn) View.VISIBLE else View.GONE

        submitChangesButton.setOnClickListener { submitChanges() }
        fetchUserData()

        // Initialize rows for basic stats, steps stats, and heart rate stats
        basicStatsRows = listOf(
            view.findViewById(R.id.ageRow),
            view.findViewById(R.id.heightRow),
            view.findViewById(R.id.weightRow)
        )
        stepsStatsRows = listOf(
            view.findViewById(R.id.stepsRow),
            view.findViewById(R.id.averageStepsRow)
        )
        heartRateStatsRows = listOf(
            view.findViewById(R.id.heartRateRow)
        )

        // Handle right and left button clicks; changes UserSession.activeSectionIndex value
        view.findViewById<Button>(R.id.rotateLeftButton).setOnClickListener {
            rotateView(-1)
        }
        view.findViewById<Button>(R.id.rotateRightButton).setOnClickListener {
            rotateView(1)
        }

        //user_stats.kt Fragment versions
        if(UserSession.activeSectionIndex==0) {
            showBasicStats()
        }
        if(UserSession.activeSectionIndex==1) {
            showStepsStats()
        }
        if(UserSession.activeSectionIndex==2) {
            showHeartRateStats()
        }
        return view
    }

    // Function to move views left or right
    private fun rotateView(direction: Int) {
        val currentSectionIndex = UserSession.activeSectionIndex
        UserSession.activeSectionIndex = (currentSectionIndex + direction + statsSections.size) % statsSections.size
        statsSections[UserSession.activeSectionIndex].invoke()
        Log.d("CURRENTSECTION", UserSession.activeSectionIndex.toString())
    }

    // Show basic stats (age, weight, height)
    private fun showBasicStats() {
        //UserSession.activeSectionIndex = 0
        descriptionTextViewUser.text = "Basic Stats for $currentUserName"
        basicStatsRows.forEach { it.visibility = View.VISIBLE }
        stepsStatsRows.forEach { it.visibility = View.GONE }
        heartRateStatsRows.forEach { it.visibility = View.GONE }
        lineChart.visibility = View.GONE
    }

    // Show steps stats (steps, average steps)
    private fun showStepsStats() {
        //UserSession.activeSectionIndex=1
        descriptionTextViewUser.text =
            "Daily Steps for $currentUserName"
        basicStatsRows.forEach { it.visibility = View.GONE }
        heartRateStatsRows.forEach { it.visibility = View.GONE }
        stepsStatsRows.forEach { it.visibility = View.VISIBLE }
        lineChart.visibility = View.VISIBLE
        fetchUserDataSteps()
    }

    // Show heart rate stats (Example)
    private fun showHeartRateStats() {
        //UserSession.activeSectionIndex=2
        descriptionTextViewUser.text =
            "Resting Heartrate for $currentUserName"
        basicStatsRows.forEach { it.visibility = View.GONE }
        stepsStatsRows.forEach { it.visibility = View.GONE }
        heartRateStatsRows.forEach { it.visibility = View.VISIBLE }
        lineChart.visibility = View.VISIBLE
        fetchUserDataHR()
    }

    //GET basic data
    private fun fetchUserData() {
        val currentUserName = UserSession.currentUserName
        firestore.collection("users")
            .whereEqualTo("displayName", currentUserName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT)
                        .show()
                    return@addOnSuccessListener
                }

                querySnapshot.documents.first().data?.let { userData ->
                    val (age, height, weight, averageDailySteps) = extractUserData(userData)
                    populateUserDataTable(age, height, weight, averageDailySteps)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun extractUserData(userData: Map<String, Any>): UserData {
        val age = (userData["age"] as? Double) ?: 0.0
        val height = (userData["height"] as? Double) ?: 0.0
        val weight = (userData["weight"] as? Double) ?: 0.0
        val averageDailySteps = (userData["averageDailySteps"] as? Double) ?: 0.0
        return UserData(age, height, weight, averageDailySteps)
    }

    private fun populateUserDataTable(age: Double, height: Double, weight: Double, averageDailySteps: Double) {
        view?.findViewById<TextView>(R.id.ageValueTextView)?.text = "$age"
        view?.findViewById<TextView>(R.id.heightValueTextView)?.text = "$height cm"
        view?.findViewById<TextView>(R.id.weightValueTextView)?.text = "$weight kg"
        view?.findViewById<TextView>(R.id.averageDailyStepsValueTextView)?.text = "$averageDailySteps steps"
        view?.findViewById<TableLayout>(R.id.userDataTable)?.visibility = View.VISIBLE
    }

    //on submit button is pressed-> send update requests to firestore
    private fun submitChanges() {
        val stepsDateText = editStepsDate.text.toString().trim()
        val stepsNumberText = editStepsNumber.text.toString().trim()
        val restingHRDateText = editHRDateTime.text.toString().trim()
        val restingHRNumberText = editHRNumber.text.toString().trim()
        val ageText = ageEditText.text.toString().trim()
        val heightText = heightEditText.text.toString().trim()
        val weightText = weightEditText.text.toString().trim()
        val averageDailyStepsText = averageDailyStepsEditText.text.toString().trim()

        val currentUserName = UserSession.currentUserName

        // Collect all changes in a map
        val updates = mutableMapOf<String, Any>().apply {
            // Update age, height, weight, and average steps if entered
            ageText.toDoubleOrNull()?.let { put("age", it) }
            heightText.toDoubleOrNull()?.let { put("height", it) }
            weightText.toDoubleOrNull()?.let { put("weight", it) }
            averageDailyStepsText.toDoubleOrNull()?.let { put("averageDailySteps", it) }
        }

        // If any basic info is updated, perform the update
        if (updates.isNotEmpty()) {
            updateUserProfile(currentUserName, updates)
        }

        // If section 1 (steps), use this validation and update function
        if (UserSession.activeSectionIndex == 1) {
            val (steps, validSteps) = validateInputs(stepsDateText, stepsNumberText)
            if (stepsDateText.isNotEmpty() && !validSteps) return
            if (steps != null) {
                updateStepsData(currentUserName, stepsDateText, steps)
            }
        }

        // If section 2 (hr), use this validation and update function
        if (UserSession.activeSectionIndex == 2) {
            val (restingHR, validRestingHR) = validateHeartRateInputs(restingHRDateText,restingHRNumberText)
            if (restingHRDateText.isNotEmpty() && !validRestingHR) return
            if (restingHR != null) {
                updateHeartRateData(currentUserName, restingHRDateText, restingHR)
            }
        }
    }

    //plots chart initially and whenever an update is made
    private fun plotChart(data: List<Pair<String, Int>>, userName: String, dataType: String) {
        // If user has no data
        if (data.isEmpty()) {
            lineChart.clear()
            lineChart.invalidate()
            return
        }
        val entries = data.map { (dateString, value) ->
            val date = LocalDate.parse(dateString)
            // Use value as y-coordinate (steps or heart rate)
            Entry(date.toEpochDay().toFloat(), value.toFloat())
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

        // OnClick of chart
        lineChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                e?.let {
                    val dateEpochDay = e.x
                    val value = e.y.toInt()
                    val date = LocalDate.ofEpochDay(dateEpochDay.toLong()).toString()

                    when (dataType) {
                        "steps" -> {
                            editStepsDate.setText(date)
                            editStepsNumber.setText(value.toString())
                        }
                        "heartRate" -> {
                            editHRDateTime.setText(date)
                            editHRNumber.setText(value.toString())
                        }
                        else -> {
                            Log.e("plotChart", "Unknown dataType: $dataType")
                        }
                    }
                }
            }
            override fun onNothingSelected() {
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

    //GET request to firestore for fetching user data related to steps
    private fun fetchUserDataSteps() {
        val currentUserName = UserSession.currentUserName
        firestore.collection("users")
            .whereEqualTo("displayName", currentUserName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                querySnapshot.documents.first().data?.let { userData->
                    //possibly refactor to eliminate this
                    val (age, height, weight, averageDailySteps) = extractUserData(userData)
                    populateUserDataTable(age, height, weight, averageDailySteps)
                    val stepsList = userData["steps"]?.toString()?.let { parseStepsJson(it) }
                    Log.d(TAG, "STEPSDATA: ${stepsList}")
                    val dataType = "steps"
                    stepsList?.let {
                        plotChart(it.sortedBy { LocalDate.parse(it.first) }, userData["displayName"] as? String ?: "Unknown User", dataType)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error fetching steps data", Toast.LENGTH_SHORT).show()
            }
    }

    //GET request to firestore for fetching user data related to heartRate
    private fun fetchUserDataHR() {
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
                    val restingHrList = userData["heartRate"]?.toString()?.let { parseRestingHeartRateJson(it) }
                    Log.d(TAG, "HRDATA: $restingHrList")
                    val dataType = "heartRate"
                    restingHrList?.let {
                        plotChart(it.sortedBy { LocalDate.parse(it.first) }, userData["displayName"] as? String ?: "Unknown User",dataType)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT).show()
            }
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

    // Function to validate heart rate inputs
    private fun validateHeartRateInputs(heartRateDateText: String, heartRateText: String): Pair<Int?, Boolean> {
        if (heartRateDateText.isNotEmpty() && !isValidDate(heartRateDateText)) {
            Toast.makeText(requireContext(), "Invalid date format. Please use YYYY-MM-DD", Toast.LENGTH_SHORT).show()
            return null to false
        }
        if (heartRateDateText.isNotEmpty() && isDateInFuture(heartRateDateText)) {
            Toast.makeText(requireContext(), "Date cannot be in the future", Toast.LENGTH_SHORT).show()
            return null to false
        }
        val hr = heartRateText.toIntOrNull()
        if (hr == null && heartRateText.isNotEmpty()) {
            Toast.makeText(requireContext(), "Please enter a valid heart rate", Toast.LENGTH_SHORT).show()
            return null to false
        }
        if (hr != null && hr > 1000) {
            Toast.makeText(requireContext(), "Please enter a valid heart rate (less than or equal to 1000)", Toast.LENGTH_SHORT).show()
            return null to false
        }
        return hr to true
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
                            // UPDATE: existing entry
                            val existingEntry = find { it.first == stepsDateText }
                            if (existingEntry != null) {
                                set(indexOf(existingEntry), existingEntry.copy(second = steps))
                            } else {
                                // CREATE: new entry
                                add(Pair(stepsDateText, steps))
                            }
                        }
                    }.sortedBy { LocalDate.parse(it.first) }

                    // Convert updated list to JSON and update Firestore
                    val dataType = "steps"
                    val updatedStepsJson = convertStepsListToJson(updatedStepsList)
                    documentRef.update("steps", updatedStepsJson)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Steps updated", Toast.LENGTH_SHORT).show()
                            plotChart(updatedStepsList, currentUserName, dataType)
                        }
                }
            }
    }

    private fun updateHeartRateData(currentUserName: String, restingHRDateText: String, restingHeartRate: Int) {
        firestore.collection("users")
            .whereEqualTo("displayName", currentUserName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val documentRef = querySnapshot.documents.firstOrNull()?.reference ?: return@addOnSuccessListener
                querySnapshot.documents.first().data?.let { userData ->
                    val heartRateList = parseRestingHeartRateJson(userData["heartRate"]?.toString() ?: "")
                    val updatedHeartRateList = heartRateList.toMutableList().apply {
                        if (restingHRDateText.isNotEmpty() && restingHeartRate == -1) {
                            // DELETE: Remove entry if heart rate is -1
                            removeIf { it.first == restingHRDateText }
                        } else {
                            // UPDATE: Modify the heart rate entry if it already exists
                            val existingEntry = find { it.first == restingHRDateText }
                            if (existingEntry != null) {
                                // Update existing entry
                                set(indexOf(existingEntry), existingEntry.copy(second = restingHeartRate))
                            } else {
                                // CREATE: Add a new entry if it does not exist
                                add(Pair(restingHRDateText, restingHeartRate))
                            }
                        }
                    }.sortedBy { LocalDate.parse(it.first) }
                    //Converts back to JSON to update firestore
                    val updatedHeartRateJson = convertHeartRateListToJson(updatedHeartRateList)
                    Log.d("Updated Heart Rate JSON", updatedHeartRateJson)
                    documentRef.update("heartRate", updatedHeartRateJson)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Heart rate updated", Toast.LENGTH_SHORT).show()
                            plotChart(updatedHeartRateList, currentUserName, "heartRate")
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT).show()
            }
    }

    //parse steps JSON from firestore request
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

    //parse heart rate JSON from firestore request
    private fun parseRestingHeartRateJson(restingHeartRateJson: String): List<Pair<String, Int>> {
        val gson = Gson()
        val jsonElement = gson.fromJson(restingHeartRateJson, JsonElement::class.java)
        Log.d(TAG, "jsonElement: $jsonElement")
        val activitiesHeartArray = jsonElement.asJsonObject.getAsJsonArray("activities-heart")
        Log.d(TAG, "activitiesHeartArray: $activitiesHeartArray")
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
    }

    //Convert to JSON for updating Firebase JSON string
    private fun convertStepsListToJson(stepsList: List<Pair<String, Int>>): String {
        val gson = Gson()
        return gson.toJson(mapOf("activities-tracker-steps" to stepsList.map { mapOf("dateTime" to it.first, "value" to it.second.toString()) }))
    }

    // Function to convert a List<Pair<String, Int>> to the original JSON for Firebase update
    private fun convertHeartRateListToJson(heartRateList: List<Pair<String, Int>>): String {
        val gson = Gson()
        val heartRateEntries = heartRateList.map { (date, restingHR) ->
            mapOf(
                "dateTime" to date,
                "value" to mapOf(
                    "customHeartRateZones" to emptyList<String>(),
                    //filler values for regular user to conform with both fitbit user and regular user dataset (Fitbit data will be overwritten anyway)
                    "heartRateZones" to listOf(
                        mapOf("caloriesOut" to 123.12, "max" to 121, "min" to 30, "minutes" to 1234, "name" to "Out of Range"),
                        mapOf("caloriesOut" to 45.24, "max" to 143, "min" to 123, "minutes" to 12, "name" to "Fat Burn")
                    ),
                    "restingHeartRate" to restingHR
                )
            )
        }
        val jsonMap = mapOf("activities-heart" to heartRateEntries)
        return gson.toJson(jsonMap)
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

    //Prevent navigation during uploads to prevent crash
    private fun disableNavigation() {
        val navigationView = requireActivity().findViewById<NavigationView>(R.id.nav_view)
        navigationView.menu.findItem(R.id.nav_home)?.isEnabled = false
        navigationView.menu.findItem(R.id.nav_userStats)?.isEnabled = false
        navigationView.menu.findItem(R.id.nav_compare)?.isEnabled = false
    }

    private fun enableNavigation() {
        val navigationView = requireActivity().findViewById<NavigationView>(R.id.nav_view)
        navigationView.menu.findItem(R.id.nav_home)?.isEnabled = true
        navigationView.menu.findItem(R.id.nav_userStats)?.isEnabled = true
        navigationView.menu.findItem(R.id.nav_compare)?.isEnabled = true
    }

    data class UserData(val age: Double, val height: Double, val weight: Double, val averageDailySteps: Double)
}
