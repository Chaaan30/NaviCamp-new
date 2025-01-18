package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve token and fullName from SharedPreferences
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val token = userPrefs.getString("token", null)
        val fullName = userPrefs.getString("fullName", null)

        if (token != null && fullName != null) {
            // Token and fullName exist, set the fullName in UserSingleton
            UserSingleton.fullName = fullName

            // Navigate to the last activity
            val lastActivityPrefs = getSharedPreferences("LastActivity", MODE_PRIVATE)
            val lastActivity = lastActivityPrefs.getString("lastActivity", null)

            if (lastActivity != null) {
                val intent = when (lastActivity) {
                    "SecurityOfficerActivity" -> Intent(this, SecurityOfficerActivity::class.java)
                    "LocomotorDisabilityActivity" -> Intent(this, LocomotorDisabilityActivity::class.java)
                    else -> null
                }
                intent?.let {
                    startActivity(it)
                    finish()
                }
            }
        } else {
            // No token or fullName, show login/register screen
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
}