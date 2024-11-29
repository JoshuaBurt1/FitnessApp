package com.example.fitnessapp

//Used to track logged in status and currently logged user through fragments
object UserSession {
    var isUserLoggedIn: Boolean = false
    var currentUserName: String = ""
    // Needed to go to corresponding Compare (User stats heart rate -> compare heart rate); start at 0 at home/login
    var activeSectionIndex: Int = 0
}