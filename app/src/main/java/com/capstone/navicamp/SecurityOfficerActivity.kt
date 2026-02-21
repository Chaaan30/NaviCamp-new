package com.capstone.navicamp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView

class SecurityOfficerActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_officer)

        //navigationView = findViewById(R.id.navigation_view)

        // 2. Setup Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Load Home Fragment by default
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.officer_fragment_container, OfficerHomeFragment())
                .commit()
            supportActionBar?.title = "Dashboard"
        }

        bottomNav.setOnItemSelectedListener { item ->
            val transaction = supportFragmentManager.beginTransaction()

            when (item.itemId) {
                R.id.nav_officer_home -> {
                    supportActionBar?.title = "Dashboard"
                    transaction.replace(R.id.officer_fragment_container, OfficerHomeFragment())
                }
                R.id.nav_officer_incidents -> {
                    supportActionBar?.title = "Incident Logs"
                    transaction.replace(R.id.officer_fragment_container, OfficerIncidentsFragment())
                }
                R.id.nav_officer_settings -> {
                    supportActionBar?.title = "Account Settings"
                    transaction.replace(R.id.officer_fragment_container, SettingsMenuFragment())
                }
            }
            transaction.commit()
            true
        }
    }

    override fun onBackPressed() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (bottomNav.selectedItemId != R.id.nav_officer_home) {
            // If they are NOT on Home, go to Home
            bottomNav.selectedItemId = R.id.nav_officer_home
        } else {
            // If they ARE on Home, close the app as usual
            super.onBackPressed()
        }
    }
}
