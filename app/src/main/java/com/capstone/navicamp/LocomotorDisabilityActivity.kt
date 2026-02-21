package com.capstone.navicamp

import androidx.appcompat.app.AlertDialog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.messaging.FirebaseMessaging
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone



class LocomotorDisabilityActivity : AppCompatActivity() {
    var isConnectedToWheelchair: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_locomotor_disability)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Load Home Fragment by default
        if (savedInstanceState == null) {
            loadFragment(LocomotorDisabledHomeFragment())
            supportFragmentManager.beginTransaction()
                .replace(R.id.pwd_fragment_container, LocomotorDisabledHomeFragment())
                .commit()
            syncConnectionStateWithDatabase()
        }

        bottomNav.setOnItemSelectedListener { item ->

            when (item.itemId) {
                R.id.nav_pwd_home -> {
                    loadFragment(LocomotorDisabledHomeFragment())
                    true
                }

                R.id.nav_pwd_scan_qr -> {
                    if (isConnectedToWheelchair) {
                        // Show the dialog. Don't switch fragments yet.
                        showGlobalDisconnectDialog()
                    } else {
                        // If not connected, load the scan fragment
                        loadFragment(LocomotorDisabledScanFragment())
                    }
                    true
                }

                R.id.nav_pwd_settings -> {
                    loadFragment(SettingsMenuFragment())
                    true
                }
                else -> false
            }

        }
    }

    private fun syncConnectionStateWithDatabase() {
        val uid = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).getString("userID", null) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val active = MySQLHelper.getActiveConnectionForUser(uid)
            withContext(Dispatchers.Main) {
                updateScanTabUI(active != null)
            }
        }
    }

    fun updateScanTabUI(isConnected: Boolean) {
        this.isConnectedToWheelchair = isConnected
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val scanItem = bottomNav.menu.findItem(R.id.nav_pwd_scan_qr)

        if (isConnected) {
            scanItem.title = "Disconnect"
            scanItem.setIcon(R.drawable.ic_bot_qr_code) // Optional: change icon too
        } else {
            scanItem.title = "Scan QR"
            scanItem.setIcon(R.drawable.ic_bot_qr_code)
        }
    }

    private fun showGlobalDisconnectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Wheelchair Connection")
            .setMessage("Do you want to disconnect from the current wheelchair?")
            .setPositiveButton("Disconnect") { _, _ ->
                // Trigger the actual database disconnect here
                performDatabaseDisconnect()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDatabaseDisconnect() {
        // 1. Get the current UserID and the DeviceID they are connected to
        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userID = sharedPreferences.getString("userID", null) ?: return

        // Show a loading dialog if you have one
        // showLoadingDialog()

        lifecycleScope.launch(Dispatchers.IO) {
            // Find which device this user is currently tied to
            val activeConnection = MySQLHelper.getActiveConnectionForUser(userID)

            if (activeConnection != null) {
                // 2. Clear the database record (Set UserID and Expiry to NULL)
                val success = MySQLHelper.updateDeviceConnectionStatus(activeConnection.deviceID, null, null)

                withContext(Dispatchers.Main) {
                    if (success) {
                        // 3. Update the Bottom Nav UI back to "Scan QR"
                        updateScanTabUI(false)

                        setBottomNavSelection(R.id.nav_pwd_home)

                        loadFragment(LocomotorDisabledHomeFragment())

                        // 4. Refresh the current fragment so the SOS button turns gray immediately
                        val currentFrag = supportFragmentManager.findFragmentById(R.id.pwd_fragment_container)
                        if (currentFrag is LocomotorDisabledHomeFragment) {
                            currentFrag.refreshStateManually()
                        } else if (currentFrag is LocomotorDisabledScanFragment) {
                            currentFrag.refreshStateManually()
                        }

                        Toast.makeText(this@LocomotorDisabilityActivity, "Disconnected successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@LocomotorDisabilityActivity, "Disconnection failed. Check internet.", Toast.LENGTH_SHORT).show()
                    }
                    // dismissLoadingDialog()
                }
            }
        }
    }

    fun setBottomNavSelection(itemId: Int) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        // This will visually move the highlight to the specified icon
        bottomNav.selectedItemId = itemId
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.pwd_fragment_container, fragment)
            .commit()
    }

}