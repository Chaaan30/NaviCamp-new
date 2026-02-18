package com.capstone.navicamp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.fragment.app.Fragment

class SecurityOfficerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_officer)

        // 2. Setup Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Load Home Fragment by default
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_officer_home
            supportFragmentManager.beginTransaction()
                .replace(R.id.officer_fragment_container, OfficerHomeFragment())
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_officer_home -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.officer_fragment_container, OfficerHomeFragment())
                        .commit()
                    true
                }
                R.id.nav_officer_incidents -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.officer_fragment_container, OfficerIncidentsFragment())
                        .commit()
                    true
                }
                R.id.nav_officer_settings -> {
                    // loadFragment(OfficerSettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.officer_fragment_container, fragment)
            .commit()
    }
}