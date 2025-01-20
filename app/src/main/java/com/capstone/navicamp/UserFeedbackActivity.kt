package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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

class UserFeedbackActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Set up the Submit button
        val submitButton: Button = findViewById(R.id.submit_button)
        submitButton.setOnClickListener {
            submitFeedback()
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

    private fun submitFeedback() {
        val feedbackEditText: EditText = findViewById(R.id.issue_description)
        val feedbackDescription = feedbackEditText.text.toString()

        if (feedbackDescription.isEmpty()) {
            Toast.makeText(this, "Please enter your feedback", Toast.LENGTH_SHORT).show()
            return
        }

        // Retrieve userID from SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val userID = sharedPreferences.getString("userID", null)

        if (userID == null) {
            Toast.makeText(this, "User ID not found. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        // Insert feedback into the database
        CoroutineScope(Dispatchers.Main).launch {
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.insertFeedback(userID, feedbackDescription)
            }

            if (success) {
                Toast.makeText(this@UserFeedbackActivity, "Feedback submitted successfully", Toast.LENGTH_SHORT).show()
                feedbackEditText.text.clear()
            } else {
                Toast.makeText(this@UserFeedbackActivity, "Failed to submit feedback. Please try again.", Toast.LENGTH_SHORT).show()
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