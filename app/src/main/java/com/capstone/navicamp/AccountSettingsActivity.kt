package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import java.text.SimpleDateFormat
import java.util.*

class AccountSettingsActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Account Information"

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Initialize navigationView
        navigationView = findViewById(R.id.navigation_view)

        // Retrieve data from SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", null)
        val userID = sharedPreferences.getString("userID", null)
        val userType = sharedPreferences.getString("userType", null)
        val email = sharedPreferences.getString("email", null)
        val contactNumber = sharedPreferences.getString("contactNumber", null)
        val createdOn = sharedPreferences.getString("createdOn", null)

        // Log the retrieved values
        Log.d("AccountSettingsActivity", "fullName: $fullName")
        Log.d("AccountSettingsActivity", "userID: $userID")
        Log.d("AccountSettingsActivity", "userType: $userType")
        Log.d("AccountSettingsActivity", "email: $email")
        Log.d("AccountSettingsActivity", "contactNumber: $contactNumber")
        Log.d("AccountSettingsActivity", "createdOn: $createdOn")

        // Format createdOn
        val formattedCreatedOn = createdOn?.let {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(it)
            outputFormat.format(date)
        }

        // Update UI with the retrieved data
        findViewById<TextView>(R.id.full_name_text)?.text = "Full Name: $fullName"
        findViewById<TextView>(R.id.user_id_text)?.text = "User ID: $userID"
        findViewById<TextView>(R.id.user_type_text)?.text = "User Type: $userType"
        findViewById<TextView>(R.id.email_text)?.text = "Email: $email"
        findViewById<TextView>(R.id.contact_number_text)?.text = "Contact Number: $contactNumber"
        findViewById<TextView>(R.id.date_created_text)?.text = "Date Created: $formattedCreatedOn"

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
                R.id.nav_item1 -> {
                    // Navigate to AccountSettingsActivity
                    val intent = Intent(this, AccountSettingsActivity::class.java)
                    startActivity(intent)
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
        // Retrieve the full name from SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", "Full Name")
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = fullName
        }
    }
}