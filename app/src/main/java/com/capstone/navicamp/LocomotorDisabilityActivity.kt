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
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
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
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var assistanceButton: Button
    private lateinit var assistanceConnectButton: Button
    private lateinit var connectionStatusTextView: TextView

    private var connectedDeviceID: String? = null
    private var connectionTimer: CountDownTimer? = null
    private var currentConnectionDurationMs: Long = 30 * 60 * 1000 // Default 30 minutes
    private var connectionExpiryTimeMillis: Long? = null

    private var currentUserID: String? = null
    private var currentUserFullName: String? = null


    private val qrCodeScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_LONG).show()
        } else {
            val scannedDeviceID = result.contents
            if (scannedDeviceID != null) {
                Log.d("QRScan", "Scanned Device ID: $scannedDeviceID")
                // Ask for duration before connecting
                showDurationSelectionDialog(scannedDeviceID)
            } else {
                Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDurationSelectionDialog(deviceID: String) {
        val durationOptions = arrayOf("15 minutes", "30 minutes", "45 minutes", "60 minutes")
        // Values in milliseconds
        val durationValuesMs = arrayOf(15 * 60 * 1000L, 30 * 60 * 1000L, 45 * 60 * 1000L, 60 * 60 * 1000L)

        AlertDialog.Builder(this)
            .setTitle("Select Connection Duration")
            .setItems(durationOptions) { dialogInterface: android.content.DialogInterface, which: Int ->
                val selectedDurationMs = durationValuesMs[which]
                connectToDevice(deviceID, selectedDurationMs) // Pass selected duration
                dialogInterface.dismiss()
            }
            .setNegativeButton("Cancel") { dialogInterface: android.content.DialogInterface, _: Int ->
                dialogInterface.dismiss()
                Toast.makeText(this, "Connection cancelled.", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false) // User must choose or cancel
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_locomotor_disability)

        // Initialize SharedPreferences for user ID and full name
        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserID = sharedPreferences.getString("userID", null)
        currentUserFullName = sharedPreferences.getString("fullName", "User")


        // Initialize navigationView
        navigationView = findViewById(R.id.navigation_view)
        connectionStatusTextView = findViewById(R.id.connection_status_textview)
        assistanceButton = findViewById(R.id.assistance_button)
        assistanceConnectButton = findViewById(R.id.assistance_connect_button)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Main Menu"

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

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

        updateConnectionStatusUI()

        // Set up the Ask for assistance button
        assistanceButton.setOnClickListener {
            if (connectedDeviceID != null && currentUserID != null && currentUserFullName != null) {
                requestAssistanceWithDevice(connectedDeviceID!!, currentUserID!!, currentUserFullName!!)
            } else if (connectedDeviceID == null) {
                Toast.makeText(this, "Please connect to a wheelchair first.", Toast.LENGTH_LONG).show()
            } else {
                 // Fallback to old assistance activity if not connected to a device or user details are missing
                Log.w("Assistance", "User ID or Full Name is null, or not connected to a device. Fallback to AssistanceActivity.")
                 val intent = Intent(this, AssistanceActivity::class.java)
                 startActivity(intent)
            }
        }

        assistanceConnectButton.setOnClickListener {
            if (connectedDeviceID == null) {
                val options = ScanOptions()
                options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                options.setPrompt("Scan QR Code on Wheelchair")
                options.setCameraId(0) // Use a specific camera of the device
                options.setBeepEnabled(true)
                options.setBarcodeImageEnabled(true)
                options.setOrientationLocked(true) // Lock to portrait
                // options.captureActivity = PortraitCaptureActivity::class.java // Use custom activity for portrait
                qrCodeScannerLauncher.launch(options)
            } else {
                // Already connected, so disconnect
                disconnectFromDevice()
            }
        }
    }

    // Overload or change connectToDevice to accept duration
    private fun connectToDevice(deviceID: String, durationMs: Long = currentConnectionDurationMs) {
        if (currentUserID == null) {
            Toast.makeText(this, "User not logged in. Cannot connect.", Toast.LENGTH_LONG).show()
            Log.e("DeviceConnect", "CurrentUserID is null. Cannot connect.")
            return
        }
        currentConnectionDurationMs = durationMs // Update the active duration

        lifecycleScope.launch {
            val currentTimeMillis = System.currentTimeMillis()
            connectionExpiryTimeMillis = currentTimeMillis + currentConnectionDurationMs // Store actual expiry time

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            dateFormat.timeZone = TimeZone.getTimeZone("Asia/Manila")
            // Ensure connectionExpiryTimeMillis is not null before formatting
            val expiryDate = connectionExpiryTimeMillis?.let { java.util.Date(it) }
            if (expiryDate == null) {
                Log.e("DeviceConnect", "connectionExpiryTimeMillis is null, cannot format date.")
                Toast.makeText(this@LocomotorDisabilityActivity, "Error setting connection time.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val connectedUntilStr = dateFormat.format(expiryDate)


            val success = withContext(Dispatchers.IO) {
                MySQLHelper.updateDeviceConnectionStatus(deviceID, currentUserID!!, connectedUntilStr)
            }

            if (success) {
                connectedDeviceID = deviceID
                val durationMinutes = currentConnectionDurationMs / (60 * 1000)
                Toast.makeText(this@LocomotorDisabilityActivity, "Connected to wheelchair: $deviceID for $durationMinutes mins", Toast.LENGTH_LONG).show()
                startConnectionTimer(currentConnectionDurationMs) // Start timer with the chosen/active duration
                updateConnectionStatusUI()
            } else {
                Toast.makeText(this@LocomotorDisabilityActivity, "Failed to connect to wheelchair $deviceID. It might be in use or an error occurred.", Toast.LENGTH_LONG).show()
                connectionExpiryTimeMillis = null // Reset if connection failed
            }
        }
    }

    private fun disconnectFromDevice(showToast: Boolean = true) {
        connectionTimer?.cancel()
        connectionTimer = null
        val previouslyConnectedDeviceID = connectedDeviceID
        connectedDeviceID = null
        connectionExpiryTimeMillis = null // Clear expiry time

        if (previouslyConnectedDeviceID != null) {
            lifecycleScope.launch {
                val disconnectSuccess = withContext(Dispatchers.IO) {
                    MySQLHelper.updateDeviceConnectionStatus(previouslyConnectedDeviceID, null, null)
                }
                if (disconnectSuccess) {
                    Log.d("DeviceConnect", "Successfully updated DB for disconnection from device: $previouslyConnectedDeviceID")
                } else {
                    Log.e("DeviceConnect", "Failed to update DB for disconnection from device: $previouslyConnectedDeviceID")
                    // Optionally, show a different toast or handle the error if DB update fails
                    Toast.makeText(this@LocomotorDisabilityActivity, "Disconnection processed. DB update issue.", Toast.LENGTH_SHORT).show()
                }

                if (showToast) {
                    Toast.makeText(this@LocomotorDisabilityActivity, "Disconnected from wheelchair: $previouslyConnectedDeviceID", Toast.LENGTH_LONG).show()
                }
                Log.d("DeviceConnect", "Local state disconnected from device: $previouslyConnectedDeviceID")
            }
        }
        updateConnectionStatusUI()
    }


    private fun startConnectionTimer(durationMs: Long) {
        connectionTimer?.cancel() // Cancel any existing timer
        connectionTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Update the global expiry time to reflect remaining time
                connectionExpiryTimeMillis = System.currentTimeMillis() + millisUntilFinished

                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                Log.d("ConnectionTimer", "Time remaining: $minutes m $seconds s for device $connectedDeviceID")
                // Update UI with remaining time
                if (connectedDeviceID != null) { // Check to prevent crash if disconnected during tick
                     connectionStatusTextView.text = "Connected to: $connectedDeviceID\nTime left: ${String.format("%02d:%02d", minutes, seconds)}"
                }
            }

            override fun onFinish() {
                Toast.makeText(this@LocomotorDisabilityActivity, "Connection timed out.", Toast.LENGTH_LONG).show()
                disconnectFromDevice(showToast = false) // Disconnect without redundant toast
            }
        }.start()
    }

    private fun updateConnectionStatusUI() {
        if (connectedDeviceID != null) {
            connectionStatusTextView.text = "Connected to Wheelchair: $connectedDeviceID"
            assistanceConnectButton.text = "Disconnect"
            // Make the original assistance button more prominent or change its text if needed
            // assistanceButton.text = "Ask Assistance (Wheelchair)"
        } else {
            connectionStatusTextView.text = "Not connected to a wheelchair"
            assistanceConnectButton.text = "Connect to Wheelchair"
            // assistanceButton.text = "Ask for assistance" // Revert if changed
        }
    }

    private fun requestAssistanceWithDevice(deviceID: String, userID: String, fullName: String) {
        lifecycleScope.launch {
            val deviceLocation = withContext(Dispatchers.IO) {
                MySQLHelper.getDeviceLastLocation(deviceID)
            }

            if (deviceLocation != null) {
                val latitude = deviceLocation.first
                val longitude = deviceLocation.second
                val floorLevelFromDb = deviceLocation.third // This can be null
                
                // Provide a default if floorLevelFromDb is null
                val floorLevelToInsert = floorLevelFromDb ?: "Unknown"

                val success = withContext(Dispatchers.IO) {
                    MySQLHelper.insertAssistanceRequestFromDevice(
                        this@LocomotorDisabilityActivity,
                        userID,
                        fullName,
                        deviceID,
                        latitude,
                        longitude,
                        floorLevelToInsert // Use the non-null version
                    )
                }
                if (success) {
                    Toast.makeText(this@LocomotorDisabilityActivity, "Assistance requested using wheelchair location!", Toast.LENGTH_LONG).show()
                    // Optionally, navigate to AssistanceActivity or show a confirmation
                    // val intent = Intent(this@LocomotorDisabilityActivity, AssistanceActivity::class.java)
                    // startActivity(intent)
                } else {
                    Toast.makeText(this@LocomotorDisabilityActivity, "Failed to send assistance request.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this@LocomotorDisabilityActivity, "Could not retrieve wheelchair location. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }


    override fun onPause() {
        super.onPause()
        // Persist connection state if activity is paused but not destroyed
        val sharedPreferences = getSharedPreferences("LocomotorPrefs", Context.MODE_PRIVATE).edit()
        sharedPreferences.putString("connectedDeviceID", connectedDeviceID)
        connectionExpiryTimeMillis?.let {
            sharedPreferences.putLong("connectionExpiryTimeMillis", it)
        } ?: sharedPreferences.remove("connectionExpiryTimeMillis")
        sharedPreferences.apply()

        val lastActivityPreferences = getSharedPreferences("LastActivity", MODE_PRIVATE)
        val editor = lastActivityPreferences.edit()
        editor.putString("lastActivity", "LocomotorDisabilityActivity")
        editor.apply()
    }

    override fun onResume() {
        super.onResume()
        // Retrieve the full name and userID from SharedPreferences
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        currentUserFullName = userPrefs.getString("fullName", "Full Name!")
        currentUserID = userPrefs.getString("userID", null) // Ensure userID is also loaded

        // Update user_fullname TextView
        findViewById<TextView>(R.id.user_fullname)?.text = currentUserFullName

        // Update nav_name_header in NavigationView
        val navView = findViewById<NavigationView>(R.id.navigation_view)
        navView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = currentUserFullName
        }

        // Restore connection state if applicable
        val locomotorPrefs = getSharedPreferences("LocomotorPrefs", Context.MODE_PRIVATE)
        val previouslyConnectedID = locomotorPrefs.getString("connectedDeviceID", null)
        val savedExpiryTimeMillis = locomotorPrefs.getLong("connectionExpiryTimeMillis", 0L)

        if (previouslyConnectedID != null && savedExpiryTimeMillis > 0L) {
            val currentTimeMillis = System.currentTimeMillis()
            if (savedExpiryTimeMillis > currentTimeMillis) {
                // Connection is still valid, resume it
                connectedDeviceID = previouslyConnectedID
                connectionExpiryTimeMillis = savedExpiryTimeMillis // Restore this crucial variable
                val remainingDurationMs = savedExpiryTimeMillis - currentTimeMillis
                if (remainingDurationMs > 0) {
                    startConnectionTimer(remainingDurationMs)
                    Log.d("Resume", "Connection resumed for $connectedDeviceID with ${remainingDurationMs / 1000}s remaining.")
                } else {
                    // Should not happen if savedExpiryTimeMillis > currentTimeMillis, but as a safeguard
                    disconnectFromDevice(showToast = false)
                    locomotorPrefs.edit().clear().apply() // Clear stale prefs
                }
            } else {
                // Connection expired while app was not active (paused or closed)
                Log.d("Resume", "Connection for $previouslyConnectedID expired while app was not active.")
                
                // Clear local state immediately if this activity instance still thought it was connected to the expired device
                if (this.connectedDeviceID == previouslyConnectedID) {
                    this.connectionTimer?.cancel()
                    this.connectionTimer = null
                    this.connectedDeviceID = null // Clear current activity's idea of connected device
                    this.connectionExpiryTimeMillis = null
                }
                
                // Attempt to update the database for the expired previouslyConnectedID
                // This ensures the DB is cleaned up even if the app was killed and onFinish/onDestroy didn't run
                lifecycleScope.launch {
                    Log.d("ResumeCleanup", "Attempting to clear expired session for $previouslyConnectedID from DB.")
                    val disconnectSuccess = withContext(Dispatchers.IO) {
                         MySQLHelper.updateDeviceConnectionStatus(previouslyConnectedID, null, null)
                    }
                    if (disconnectSuccess) {
                        Log.d("ResumeCleanup", "Successfully cleared expired session for $previouslyConnectedID from DB.")
                    } else {
                        Log.e("ResumeCleanup", "Failed to clear expired session for $previouslyConnectedID from DB (it might have been cleared by another process or was already clear).")
                    }
                }
                locomotorPrefs.edit().clear().apply() // Clear stale prefs for this device
            }
        }
        // Always update UI based on the potentially restored or cleared state
        updateConnectionStatusUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionTimer?.cancel()
        // It's good practice to ensure disconnection if the activity is destroyed while connected.
        // However, if the app is killed, this might not run.
        // The server-side 'connectedUntil' field is the ultimate arbiter.
        if (connectedDeviceID != null) {
            Log.d("Destroy", "Activity destroyed. Attempting to disconnect from $connectedDeviceID")
            // This DB call in onDestroy is not guaranteed.
            // Consider a service or WorkManager for critical cleanup if needed.
            // For now, we rely on the timer and server-side expiry.
            // To be safe, we can try a quick disconnect, but it's not foolproof.
             lifecycleScope.launch(Dispatchers.IO) { // Fire-and-forget on IO thread
                 MySQLHelper.updateDeviceConnectionStatus(connectedDeviceID!!, null, null)
             }
        }
    }
}
