package com.example.fitnessapp

//Used to track logged in status and currently logged user through fragments
object UserSession {
    var isUserLoggedIn: Boolean = false
    var currentUserName: String = ""
}