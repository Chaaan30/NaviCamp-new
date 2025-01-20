package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistanceActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistance)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Request Assistance"

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Initialize navigationView
        navigationView = findViewById(R.id.navigation_view)

        // Retrieve userID from SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)

        // Set up the Request Assistance button
        val assistanceButton: Button = findViewById(R.id.requestAssistanceButton)
        assistanceButton.setOnClickListener {
            Log.d("AssistanceActivity", "Request Assistance button clicked")

            val userID = sharedPreferences.getString("userID", null)
            val fullName = UserSingleton.fullName

            Log.d("AssistanceActivity", "User ID: $userID, Full Name: $fullName")

            if (userID != null && fullName != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    val success = withContext(Dispatchers.IO) {
                        MySQLHelper.insertLocationData(userID, fullName)
                    }
                    if (success) {
                        Log.d("AssistanceActivity", "Location data inserted successfully")
                        Toast.makeText(this@AssistanceActivity, "Assistance requested successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("AssistanceActivity", "Failed to insert location data")
                        Toast.makeText(this@AssistanceActivity, "Failed to request assistance. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.e("AssistanceActivity", "User ID or Full Name is null")
                Toast.makeText(this, "User ID or Full Name is missing. Please log in again.", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up NavigationView item click listener
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    // Clear SharedPreferences
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
                R.id.nav_item2 -> {
                    // Navigate to LocomotorDisabilityActivity and clear the activity stack
                    val intent = Intent(this, LocomotorDisabilityActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
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