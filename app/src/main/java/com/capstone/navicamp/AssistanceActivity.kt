package com.capstone.navicamp

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistanceActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var assistanceButton: Button
    private var loadingDialog: Dialog? = null

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
                R.id.nav_item1 -> {
                    // Navigate to AccountSettingsActivity
                    val intent = Intent(this, AccountSettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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

        // Retrieve userID and fullName from SharedPreferences and set them in UserSingleton
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        UserSingleton.userID = sharedPreferences.getString("userID", null)
        DeviceSingleton.deviceID = sharedPreferences.getString("deviceID", null)
        UserSingleton.fullName = sharedPreferences.getString("fullName", null)

        // Set up the Request Assistance button
        assistanceButton = findViewById(R.id.requestAssistanceButton)

        assistanceButton.setOnClickListener {
            Log.d("AssistanceActivity", "Request Assistance button clicked")

            val userID = UserSingleton.userID
            val deviceID = DeviceSingleton.deviceID
            val fullName = UserSingleton.fullName

            Log.d("AssistanceActivity", "User ID: $userID, Device ID: $deviceID, Full Name: $fullName")

            if (userID != null && deviceID != null && fullName != null) {
                // Show loading dialog
                showLoadingDialog()

                CoroutineScope(Dispatchers.Main).launch {
                    val success = withContext(Dispatchers.IO) {
                        MySQLHelper.insertLocationData(this@AssistanceActivity, userID, deviceID, fullName)
                    }
                    if (success) {
                        Log.d("AssistanceActivity", "Location data inserted successfully")
                        Toast.makeText(this@AssistanceActivity, "Assistance requested successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("AssistanceActivity", "Failed to insert location data")
                        Toast.makeText(this@AssistanceActivity, "Failed to request assistance. Please try again.", Toast.LENGTH_SHORT).show()
                    }

                    // Dismiss loading dialog
                    dismissLoadingDialog()
                }
            } else {
                Log.e("AssistanceActivity", "User ID or Full Name is null")
                Toast.makeText(this, "User ID or Full Name is missing. Please log in again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoadingDialog() {
        loadingDialog = Dialog(this).apply {
            setContentView(R.layout.dialog_loading)
            setCancelable(false)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
    }

    override fun onResume() {
        super.onResume()
        // Retrieve the full name from SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", "Full Name")

        // Update nav_name_header in NavigationView
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = fullName
        }
    }
}