package com.example.fitnessapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Highscores fragment
 */
class Highscores : Fragment() {
    private lateinit var highScore1: TextView
    private lateinit var highScore2: TextView
    private lateinit var highScore3: TextView
    private val shared = "passHighscore"

    /**
     * Create the highscores fragment view
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_highscores, container, false)

        highScore1 = view.findViewById(R.id.highScore1)
        highScore2 = view.findViewById(R.id.highScore2)
        highScore3 = view.findViewById(R.id.highScore3)

        //clearHighScores()
        updateHighScores()
        return view
    }

    /**
     * Update highscores from SharedPreferences
     */
    private fun updateHighScores() {
        val prefs = requireActivity().getSharedPreferences(shared, Context.MODE_PRIVATE)
        val score1 = prefs.getInt("score1", 0)
        val score2 = prefs.getInt("score2", 0)
        val score3 = prefs.getInt("score3", 0)
        val player1 = prefs.getString("player1", "")
        val player2 = prefs.getString("player2", "")
        val player3 = prefs.getString("player3", "")
        highScore1.text = "$player1: $score1"
        highScore2.text = "$player2: $score2"
        highScore3.text = "$player3: $score3"
    }

    /**
     * Clear highscores
     */
    /*
    private fun clearHighScores() {
        val editor = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        editor.clear() // Clear all entries
        editor.apply()
    }*/
}