package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import android.widget.Button
import android.widget.TextView

class AssistanceActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistance)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Set up the Request Assistance button
        val assistanceButton: Button = findViewById(R.id.requestAssistanceButton)
        assistanceButton.setOnClickListener {
            // Handle button click logic here (e.g., open a dialog or perform an action)
        }

        // Set up NavigationView item click listener
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    // Clear SharedPreferences
                    val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.clear()
                    editor.apply()

                    // Navigate to MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update nav_name_header in NavigationView
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = UserSingleton.fullName
        }
    }
}
