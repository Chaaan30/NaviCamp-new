package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if setup has been completed
        val appPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val setupCompleted = appPrefs.getBoolean("setup_completed", false)
        
        if (!setupCompleted) {
            // First time user - redirect to setup
            val intent = Intent(this, SetupActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Retrieve token, fullName, and userType from SharedPreferences
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val isLoggedIn = userPrefs.getBoolean("isLoggedIn", false)
        val fullName = userPrefs.getString("fullName", null)
        val systemRole = userPrefs.getString("systemRole", null)
        val userType = userPrefs.getString("userType", null)

        if (isLoggedIn && fullName != null && userType != null) {
            // User is logged in, set the fullName in UserSingleton
            UserSingleton.fullName = fullName

            // Navigate to the appropriate activity based on userType
            val normalizedRole = systemRole?.trim()?.lowercase()
            val intent = when {
                normalizedRole?.contains("safety") == true ||
                    normalizedRole?.contains("security") == true ||
                    normalizedRole?.contains("officer") == true ->
                    Intent(this, SecurityOfficerActivity::class.java)
                normalizedRole?.contains("disabled") == true ||
                    normalizedRole?.contains("tempor") == true ||
                    normalizedRole?.contains("perman") == true ||
                    normalizedRole?.contains("pwd") == true ->
                    Intent(this, LocomotorDisabilityActivity::class.java)
                else -> when (userType) {
                    "Safety Officer", "Security Officer" -> Intent(this, SecurityOfficerActivity::class.java)
                    "Temporarily Disabled", "Permanently Disabled" -> Intent(this, LocomotorDisabilityActivity::class.java)
                    else -> null
                }
            }
            intent?.let {
                startActivity(it)
                finish()
                return
            }
        }

        // Show login/register actions whenever auto-navigation did not happen.
        val showRegisterButton = findViewById<Button>(R.id.show_register_button)
        showRegisterButton.setOnClickListener {
            val registerBottomSheet = RegisterBottomSheet()
            registerBottomSheet.show(supportFragmentManager, "RegisterBottomSheet")
        }

        val showLoginButton = findViewById<Button>(R.id.show_login_button)
        showLoginButton.setOnClickListener {
            val loginBottomSheet = LoginBottomSheet()
            loginBottomSheet.show(supportFragmentManager, "LoginBottomSheet")
        }
    }
}