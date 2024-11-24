package com.example.fitnessapp

// Data class to hold all user statistics in a single cluster in MongoDB
data class User(
    val username: String,
    val password: String,
    val clientId: String,
    val fitbitAccessToken: String,
    val age: Double,
    val gender: String,
    val height: Double,
    val weight: Double,
    val memberSince: String,
    val averageDailySteps: Double,

    //Planned on Charting these values, but no native graphing feature in android?

    val steps: List<Pair<String, Int>>,  // List of tuples [dateTime, steps]
    //val restingHR: List<Pair<Int,String>>, //List of tuples [restingHeartRate,dateTime]
    //val calories: List<Pair<String, Int>>, // List of tuples [dateTime, calories]
    //val cardioScore: List<Pair<String, String>>, // List of tuples [dateTime, vo2Max]
    //val restingHR: List<Pair<String, Int>>, //List of tuples [dateTime, restingHeartRate]
    //val heartRate: List<Pair<String, Int>> //List of tuples [dateTime, heartRate]
)
