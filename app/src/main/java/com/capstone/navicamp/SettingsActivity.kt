package com.capstone.navicamp

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.bottomnavigation.BottomNavigationView

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val txtName: TextView = findViewById(R.id.txt_full_name)
        val btnGoToAccount: ConstraintLayout = findViewById(R.id.profile_section)
        val btnTermsAndConditions: TextView = findViewById(R.id.btn_privacy )
        val btnLogout: TextView = findViewById(R.id.btn_logout)
        val btnDeviceSetup: TextView = findViewById(R.id.btn_device_setup)
        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_navigation)

        // 1. Set the User Name from SharedPrefs
        val sharedPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        txtName.text = sharedPrefs.getString("fullName", "User Name")

        // 2. Navigation to Account Settings
        // Set a single listener on the entire section.
        // Clicks on the arrow button will propagate up to this container.
        btnGoToAccount.setOnClickListener {
            val intent = Intent(this, AccountSettingsActivity::class.java)
            startActivity(intent)
        }

        // 3. Logout Logic
        btnLogout.setOnClickListener {
            sharedPrefs.edit().clear().apply() // Reset Login
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // 4. Terms & Conditions
        btnTermsAndConditions.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_terms_conditions, null)

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("OK", null) // 'null' just closes it by default
                .show()
        }

        // 4. Device Setup Logic
        btnDeviceSetup.setOnClickListener {
            val intent = Intent(this, SetupActivity::class.java)
            startActivity(intent)
        }

        // 5. Handle Bottom Navigation Highlighting
        bottomNav.selectedItemId = R.id.nav_settings
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    finish()
                    true
                }
                R.id.nav_scan_qr -> {
                    // Logic to go back and trigger QR from home
                    val intent = Intent(this, LocomotorDisabilityActivity::class.java)
                    intent.putExtra("TRIGGER_SCAN", true)
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}