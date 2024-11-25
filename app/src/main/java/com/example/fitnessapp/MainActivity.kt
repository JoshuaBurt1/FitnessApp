package com.example.fitnessapp

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

//starts the main activity, starts the navigation bar view
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //app must always use light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        navigationView.setNavigationItemSelectedListener(this)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.nav_open,
            R.string.nav_close
        )
        drawerLayout.addDrawerListener(toggle)

        toggle.syncState()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, Home())
                .commit()
        }
    }

    //changes the fragments
    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.nav_home -> supportFragmentManager.beginTransaction().replace(R.id.fragment_container, Home()).commit()
            R.id.nav_game -> supportFragmentManager.beginTransaction().replace(R.id.fragment_container, Game()).commit()
            R.id.nav_high_score -> supportFragmentManager.beginTransaction().replace(R.id.fragment_container, Highscores()).commit()
        }
        navigationView.setCheckedItem(menuItem.itemId)
        drawerLayout.closeDrawer(GravityCompat.START)
        return false
    }
}