package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check battery optimization on app startup
        checkBatteryOptimization()

        // Retrieve token, fullName, and userType from SharedPreferences
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val isLoggedIn = userPrefs.getBoolean("isLoggedIn", false)
        val fullName = userPrefs.getString("fullName", null)
        val userType = userPrefs.getString("userType", null)

        if (isLoggedIn && fullName != null && userType != null) {
            // User is logged in, set the fullName in UserSingleton
            UserSingleton.fullName = fullName

            // Navigate to the appropriate activity based on userType
            val intent = when (userType) {
                "Security Officer" -> Intent(this, SecurityOfficerActivity::class.java)
                "Personnel", "Student", "Visitor" -> Intent(
                    this,
                    LocomotorDisabilityActivity::class.java
                )

                else -> null
            }
            intent?.let {
                startActivity(it)
                finish()
            }
        } else {
            // No token, fullName, or userType, show login/register screen
            setContentView(R.layout.activity_main)
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

    private fun checkBatteryOptimization() {
        // Check if we should show battery optimization dialog
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val hasShownDialog = prefs.getBoolean("battery_optimization_dialog_shown", false)
        
        if (!hasShownDialog && !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            // Show dialog after a short delay to let the UI settle
            window.decorView.postDelayed({
                BatteryOptimizationHelper.showBatteryOptimizationDialog(this)
                // Mark as shown so we don't show it every time
                prefs.edit().putBoolean("battery_optimization_dialog_shown", true).apply()
            }, 1000)
        }
    }
}