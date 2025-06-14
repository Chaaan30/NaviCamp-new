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

// BLE imports for Line Follower
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.cardview.widget.CardView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.UUID

class LocomotorDisabilityActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var assistanceButton: Button
    private lateinit var assistanceConnectButton: Button
    private lateinit var connectionStatusTextView: TextView

    // Line Follower BLE Components
    private lateinit var lineFollowerCard: CardView
    private lateinit var lineFollowerToggle: SwitchMaterial
    private lateinit var lineFollowerStatus: TextView
    
    // BLE Variables
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var bleCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var isBLEConnected = false
    private var targetBLEDeviceName: String? = null
    private var autoReconnectEnabled = false
    private var intentionalDisconnect = false
    private var authorizedDeviceID: String? = null // QR authorization
    private var currentFloor: String? = null // Floor detection
    
    // BLE Constants
    private val BLE_SCAN_TIMEOUT = 10000L // 10 seconds
    private val REQUEST_BLUETOOTH_PERMISSIONS = 1001
    private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val CHARACTERISTIC_UUID = UUID.fromString("87654321-4321-4321-4321-cba987654321")
    private val FLOOR_SCAN_INTERVAL = 30000L // Scan for floor beacons every 30 seconds

    private var connectedDeviceID: String? = null
    private var connectionTimer: CountDownTimer? = null
    private var currentConnectionDurationMs: Long = 30 * 60 * 1000 // Default 30 minutes
    private var connectionExpiryTimeMillis: Long? = null
    private var isRestoringConnection = false // Flag to prevent multiple restoration attempts

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
        Log.d("QRScan", "Showing duration selection dialog for device: $deviceID")
        val durationOptions = arrayOf("15 minutes", "30 minutes", "45 minutes", "60 minutes")
        // Values in milliseconds
        val durationValuesMs = arrayOf(15 * 60 * 1000L, 30 * 60 * 1000L, 45 * 60 * 1000L, 60 * 60 * 1000L)

        AlertDialog.Builder(this)
            .setTitle("Select Connection Duration")
            .setItems(durationOptions) { dialogInterface: android.content.DialogInterface, which: Int ->
                val selectedDurationMs = durationValuesMs[which]
                val selectedDurationMinutes = selectedDurationMs / (60 * 1000)
                Log.d("QRScan", "User selected $selectedDurationMinutes minutes for device $deviceID")
                connectToDevice(deviceID, selectedDurationMs) // Pass selected duration
                dialogInterface.dismiss()
            }
            .setNegativeButton("Cancel") { dialogInterface: android.content.DialogInterface, _: Int ->
                Log.d("QRScan", "User cancelled connection to device $deviceID")
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

        Log.d("LocomotorDisability", "=== APP STARTING ===")
        Log.d("LocomotorDisability", "Current User ID: $currentUserID")
        Log.d("LocomotorDisability", "Connected Device ID (should be null): $connectedDeviceID")
        Log.d("LocomotorDisability", "Connection Expiry Time (should be null): $connectionExpiryTimeMillis")

        // Set UserSingleton values for FCM token management
        UserSingleton.userID = currentUserID
        UserSingleton.fullName = currentUserFullName


        // Initialize navigationView
        navigationView = findViewById(R.id.navigation_view)
        connectionStatusTextView = findViewById(R.id.connection_status_textview)
        assistanceButton = findViewById(R.id.assistance_button)
        assistanceConnectButton = findViewById(R.id.assistance_connect_button)

        // Initialize Line Follower components
        lineFollowerCard = findViewById(R.id.line_follower_card)
        lineFollowerToggle = findViewById(R.id.line_follower_toggle)
        lineFollowerStatus = findViewById(R.id.line_follower_status)
        
        // Initialize BLE
        initializeBLE()
        
        // Setup Line Follower toggle listener
        setupLineFollowerToggle()

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

                R.id.nav_device_setup -> {
                    // Start device setup
                    startDeviceSetup()
                    true
                }
                else -> false
            }
        }

        // Don't update connection status UI immediately - wait for database restoration
        // updateConnectionStatusUI()
        
        // Show loading state while checking for existing connections
        connectionStatusTextView.text = "Checking for active wheelchair connection..."
        assistanceConnectButton.text = "Checking..."
        assistanceConnectButton.isEnabled = false

        // Register FCM token for this user
        registerFCMToken()
        
        // Initialize user name display
        initializeUserName()
        
        // ALWAYS check for active connections from database and restore state
        // This ensures connection is restored even when activity is started from push notifications
        restoreConnectionFromDatabase()
        
        // Clean up any expired connections in the database
        lifecycleScope.launch(Dispatchers.IO) {
            val cleanedUp = MySQLHelper.cleanupExpiredConnections()
            if (cleanedUp > 0) {
                Log.d("LocomotorDisability", "Cleaned up $cleanedUp expired device connections on activity start")
            }
        }

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
            Log.d("ButtonClick", "=== ASSISTANCE CONNECT BUTTON CLICKED ===")
            Log.d("ButtonClick", "Current connectedDeviceID: $connectedDeviceID")
            Log.d("ButtonClick", "Button text: ${assistanceConnectButton.text}")
            
            if (connectedDeviceID == null) {
                Log.d("ButtonClick", "No device connected, starting QR scan")
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
                Log.d("ButtonClick", "Device connected, initiating disconnect")
                disconnectFromDevice()
            }
        }
    }

    private fun registerFCMToken() {
        if (currentUserID != null) {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("LocomotorDisability", "Fetching FCM registration token failed", task.exception)
                        return@addOnCompleteListener
                    }

                    // Get new FCM registration token
                    val token = task.result
                    Log.d("LocomotorDisability", "FCM Token: $token")

                    // Update token in database
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val success = MySQLHelper.updateUserFCMToken(currentUserID!!, token)
                            if (success) {
                                Log.d("LocomotorDisability", "FCM token updated successfully for user: $currentUserID")
                            } else {
                                Log.e("LocomotorDisability", "Failed to update FCM token for user: $currentUserID")
                            }
                        } catch (e: Exception) {
                            Log.e("LocomotorDisability", "Error updating FCM token: ${e.message}", e)
                        }
                    }
                }
        } else {
            Log.w("LocomotorDisability", "Cannot register FCM token - currentUserID is null")
        }
    }

    // Overload or change connectToDevice to accept duration
    private fun connectToDevice(deviceID: String, durationMs: Long = currentConnectionDurationMs) {
        Log.d("LocomotorDisability", "=== CONNECT TO DEVICE CALLED ===")
        Log.d("LocomotorDisability", "Device ID: $deviceID")
        Log.d("LocomotorDisability", "Duration: ${durationMs / (60 * 1000)} minutes")
        Log.d("LocomotorDisability", "Current User ID: $currentUserID")
        
        if (currentUserID == null) {
            Toast.makeText(this, "User not logged in. Cannot connect.", Toast.LENGTH_LONG).show()
            Log.e("DeviceConnect", "CurrentUserID is null. Cannot connect.")
            return
        }
        currentConnectionDurationMs = durationMs // Update the active duration

        lifecycleScope.launch {
            // First check if device is available
            val isAvailable = withContext(Dispatchers.IO) {
                MySQLHelper.isDeviceAvailable(deviceID)
            }
            
            Log.d("LocomotorDisability", "=== DEVICE AVAILABILITY CHECK ===")
            Log.d("LocomotorDisability", "Device $deviceID is available: $isAvailable")
            
            if (!isAvailable) {
                // Check if device is under maintenance for specific error message
                val deviceStatus: String? = withContext(Dispatchers.IO) {
                    MySQLHelper.getDeviceStatus(deviceID)
                }
                
                val errorMessage = when (deviceStatus?.lowercase()) {
                    "maintenance" -> "Wheelchair $deviceID is currently under maintenance and cannot be used. Please contact support or try another wheelchair."
                    "in_use" -> "Wheelchair $deviceID is currently in use by another user. Please try again later."
                    else -> "Wheelchair $deviceID is currently unavailable. Please try again later."
                }
                
                Toast.makeText(this@LocomotorDisabilityActivity, errorMessage, Toast.LENGTH_LONG).show()
                Log.w("DeviceConnect", "Device $deviceID is not available for connection - Status: $deviceStatus")
                return@launch
            }
            
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

            Log.d("DeviceConnect", "=== ATTEMPTING DATABASE UPDATE ===")
            Log.d("DeviceConnect", "Attempting to connect to device $deviceID until $connectedUntilStr")

            val success = withContext(Dispatchers.IO) {
                MySQLHelper.updateDeviceConnectionStatus(deviceID, currentUserID!!, connectedUntilStr)
            }

            Log.d("DeviceConnect", "=== DATABASE UPDATE RESULT ===")
            Log.d("DeviceConnect", "Database update success: $success")

            if (success) {
                connectedDeviceID = deviceID
                val durationMinutes = currentConnectionDurationMs / (60 * 1000)
                Toast.makeText(this@LocomotorDisabilityActivity, "Successfully connected to wheelchair: $deviceID for $durationMinutes mins", Toast.LENGTH_LONG).show()
                Log.d("DeviceConnect", "Successfully connected to device $deviceID")
                startConnectionTimer(currentConnectionDurationMs) // Start timer with the chosen/active duration
                updateConnectionStatusUI()
                
                // Start BLE connection for Line Follower
                startBLEConnection(deviceID)
            } else {
                Toast.makeText(this@LocomotorDisabilityActivity, "Failed to connect to wheelchair $deviceID. It might be in use or an error occurred.", Toast.LENGTH_LONG).show()
                Log.e("DeviceConnect", "Failed to connect to device $deviceID - database update failed")
                connectionExpiryTimeMillis = null // Reset if connection failed
            }
        }
    }

    private fun disconnectFromDevice(showToast: Boolean = true) {
        Log.d("DeviceConnect", "=== DISCONNECT FROM DEVICE CALLED ===")
        Log.d("DeviceConnect", "Previously connected device: $connectedDeviceID")
        
        connectionTimer?.cancel()
        connectionTimer = null
        val previouslyConnectedDeviceID = connectedDeviceID
        connectedDeviceID = null
        connectionExpiryTimeMillis = null // Clear expiry time

        // Disconnect BLE for Line Follower
        disconnectBLE()

        if (previouslyConnectedDeviceID != null) {
            lifecycleScope.launch {
                Log.d("DeviceConnect", "=== ATTEMPTING DATABASE DISCONNECT ===")
                Log.d("DeviceConnect", "Disconnecting device: $previouslyConnectedDeviceID")
                
                val disconnectSuccess = withContext(Dispatchers.IO) {
                    MySQLHelper.updateDeviceConnectionStatus(previouslyConnectedDeviceID, null, null)
                }
                
                Log.d("DeviceConnect", "=== DATABASE DISCONNECT RESULT ===")
                Log.d("DeviceConnect", "Database disconnect success: $disconnectSuccess")
                
                if (disconnectSuccess) {
                    Log.d("DeviceConnect", "Successfully updated DB for disconnection from device: $previouslyConnectedDeviceID")
                    
                    // Verify the disconnect worked by checking device status
                    val deviceStatus = withContext(Dispatchers.IO) {
                        MySQLHelper.getDeviceStatus(previouslyConnectedDeviceID)
                    }
                    Log.d("DeviceConnect", "Device $previouslyConnectedDeviceID status after disconnect: $deviceStatus")
                    
                    // Additional verification - check database status
                    checkDatabaseStatus(previouslyConnectedDeviceID)
                    
                } else {
                    Log.e("DeviceConnect", "Failed to update DB for disconnection from device: $previouslyConnectedDeviceID")
                    Toast.makeText(this@LocomotorDisabilityActivity, "Disconnection processed. DB update issue.", Toast.LENGTH_SHORT).show()
                }

                if (showToast) {
                    Toast.makeText(this@LocomotorDisabilityActivity, "Disconnected from wheelchair: $previouslyConnectedDeviceID", Toast.LENGTH_LONG).show()
                }
                Log.d("DeviceConnect", "Local state disconnected from device: $previouslyConnectedDeviceID")
            }
        } else {
            Log.d("DeviceConnect", "No device was connected, nothing to disconnect")
        }
        updateConnectionStatusUI()
    }


    private fun startConnectionTimer(durationMs: Long) {
        connectionTimer?.cancel() // Cancel any existing timer
        connectionTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Don't update connectionExpiryTimeMillis here - it should remain fixed
                // connectionExpiryTimeMillis = System.currentTimeMillis() + millisUntilFinished

                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                Log.d("ConnectionTimer", "Time remaining: $minutes m $seconds s for device $connectedDeviceID")
                // Update UI with remaining time
                if (connectedDeviceID != null) { // Check to prevent crash if disconnected during tick
                     connectionStatusTextView.text = "Connected to: $connectedDeviceID\nTime left: ${String.format("%02d:%02d", minutes, seconds)}"
                }
            }

            override fun onFinish() {
                Toast.makeText(this@LocomotorDisabilityActivity, "You have been disconnected from the wheelchair due to time limit.", Toast.LENGTH_LONG).show()
                disconnectFromDevice(showToast = false) // Disconnect without redundant toast
            }
        }.start()
    }

    private fun updateConnectionStatusUI() {
        if (connectedDeviceID != null) {
            connectionStatusTextView.text = "Connected to Wheelchair: $connectedDeviceID"
            assistanceConnectButton.text = "Disconnect"
            assistanceConnectButton.isEnabled = true
            // Make the original assistance button more prominent or change its text if needed
            // assistanceButton.text = "Ask Assistance (Wheelchair)"
        } else {
            connectionStatusTextView.text = "Not connected to a wheelchair"
            assistanceConnectButton.text = "Connect to Wheelchair"
            assistanceConnectButton.isEnabled = true
            // assistanceButton.text = "Ask for assistance" // Revert if changed
        }
    }

    private fun requestAssistanceWithDevice(deviceID: String, userID: String, fullName: String) {
        lifecycleScope.launch {
            // Get user's full name from user_table based on userID
            // If it fails, we'll use the fullName parameter as fallback
            val userFullName = withContext(Dispatchers.IO) {
                try {
                    MySQLHelper.getUserFullNameByUserID(userID)
                } catch (e: Exception) {
                    Log.e("LocomotorDisability", "Failed to get fullName from database: ${e.message}")
                    null
                }
            } ?: fullName // Fallback to current fullName if DB query fails

            Log.d("LocomotorDisability", "Using fullName: '$userFullName' for userID: $userID")

            // Get device location data from devices_table based on deviceID
            val deviceLocation = withContext(Dispatchers.IO) {
                MySQLHelper.getDeviceLastLocation(deviceID)
            }

            if (deviceLocation != null) {
                val latitude = deviceLocation.first
                val longitude = deviceLocation.second
                val floorLevelFromDb = deviceLocation.third // This can be null
                
                // Provide a default if floorLevelFromDb is null
                val floorLevelToInsert = floorLevelFromDb ?: "Unknown"

                Log.d("LocomotorDisability", "Device location: lat=$latitude, lng=$longitude, floor=$floorLevelToInsert")

                val success = withContext(Dispatchers.IO) {
                    MySQLHelper.insertAssistanceRequestFromDevice(
                        this@LocomotorDisabilityActivity,
                        userID,
                        userFullName, // Use fullName from user_table or fallback
                        deviceID,
                        latitude,
                        longitude,
                        floorLevelToInsert
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
        
        // Save last activity for navigation purposes
        val lastActivityPreferences = getSharedPreferences("LastActivity", MODE_PRIVATE)
        val editor = lastActivityPreferences.edit()
        editor.putString("lastActivity", "LocomotorDisabilityActivity")
        editor.apply()
    }

    override fun onResume() {
        super.onResume()
        
        Log.d("LocomotorDisability", "=== ON RESUME CALLED ===")
        Log.d("LocomotorDisability", "Connected Device ID: $connectedDeviceID")
        
        // Update name in nav header upon resume
        val fullName = if (UserSingleton.fullName.isNullOrBlank()) {
            val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            val storedName = sharedPreferences.getString("fullName", "User")
            UserSingleton.fullName = storedName // Update singleton
            storedName
        } else {
            UserSingleton.fullName
        }

        findViewById<TextView>(R.id.user_fullname)?.text = fullName
        
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = fullName
        }

        // ALWAYS restore connection from database on resume
        // This ensures connection state is maintained across app lifecycle events
        Log.d("LocomotorDisability", "Restoring connection from database on resume")
        
        // Show loading state while restoring connection
        if (!isRestoringConnection) {
            connectionStatusTextView.text = "Checking for active wheelchair connection..."
            assistanceConnectButton.text = "Checking..."
            assistanceConnectButton.isEnabled = false
        }
        
        restoreConnectionFromDatabase()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionTimer?.cancel()
        
        // Cleanup BLE connection
        disconnectBLE()
        
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

    private fun startDeviceSetup() {
        val intent = Intent(this, SetupActivity::class.java)
        // Add flag to indicate we want to return to main activity after setup
        intent.putExtra("RETURN_TO_MAIN", true)
        startActivity(intent)
    }

    private fun initializeUserName() {
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", "User")
        
        // Ensure UserSingleton is properly set
        UserSingleton.fullName = fullName
        
        // Log for debugging
        Log.d("LocomotorDisability", "Initializing name: $fullName")
        
        // Update UI elements with the name
        findViewById<TextView>(R.id.user_fullname)?.text = fullName
        
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = fullName
        }
    }

    private fun restoreConnectionFromDatabase() {
        if (currentUserID == null) {
            Log.w("LocomotorDisability", "Cannot restore connection - currentUserID is null")
            return
        }
        
        if (isRestoringConnection) {
            Log.d("LocomotorDisability", "Connection restoration already in progress, skipping")
            return
        }
        
        isRestoringConnection = true
        
        Log.d("LocomotorDisability", "=== ATTEMPTING DATABASE RESTORATION ===")
        Log.d("LocomotorDisability", "Attempting to restore connection from database for user: $currentUserID")
        Log.d("LocomotorDisability", "Current local state - connectedDeviceID: $connectedDeviceID, timer: ${if (connectionTimer != null) "running" else "null"}")
        
        lifecycleScope.launch {
            try {
                // First, let's check what's actually in the database for debugging
                withContext(Dispatchers.IO) {
                    val deviceStatus: String? = MySQLHelper.getDeviceStatus("202501")
                    Log.d("LocomotorDisability", "Device 202501 current status: $deviceStatus")
                    
                    // Monitor for 'active' status
                    MySQLHelper.monitorDeviceStatusChanges("202501")
                }
                
                val activeConnection = withContext(Dispatchers.IO) {
                    MySQLHelper.getActiveConnectionForUser(currentUserID!!)
                }
                
                Log.d("LocomotorDisability", "=== DATABASE QUERY RESULT ===")
                Log.d("LocomotorDisability", "Database query result: ${if (activeConnection != null) "Found connection to ${activeConnection.deviceID}" else "No active connection found"}")
                
                if (activeConnection != null) {
                    val currentTimeMillis = System.currentTimeMillis()
                    
                    Log.d("LocomotorDisability", "=== CONNECTION DETAILS ===")
                    Log.d("LocomotorDisability", "Device ID: ${activeConnection.deviceID}")
                    Log.d("LocomotorDisability", "Current time: $currentTimeMillis")
                    Log.d("LocomotorDisability", "Connection expires: ${activeConnection.connectedUntilMillis}")
                    Log.d("LocomotorDisability", "Time difference: ${activeConnection.connectedUntilMillis - currentTimeMillis}ms")
                    
                    if (activeConnection.connectedUntilMillis > currentTimeMillis) {
                        // Connection is still valid, restore it
                        Log.d("LocomotorDisability", "=== RESTORING CONNECTION ===")
                        
                        // Cancel any existing timer to prevent duplicates
                        connectionTimer?.cancel()
                        connectionTimer = null
                        
                        // Restore connection state
                        connectedDeviceID = activeConnection.deviceID
                        connectionExpiryTimeMillis = activeConnection.connectedUntilMillis
                        
                        val remainingDurationMs = activeConnection.connectedUntilMillis - currentTimeMillis
                        val remainingMinutes = remainingDurationMs / (60 * 1000)
                        val remainingSeconds = (remainingDurationMs % (60 * 1000)) / 1000
                        
                        Log.d("LocomotorDisability", "=== TIMING DETAILS ===")
                        Log.d("LocomotorDisability", "Database expiry time: ${activeConnection.connectedUntilMillis}")
                        Log.d("LocomotorDisability", "Current system time: $currentTimeMillis")
                        Log.d("LocomotorDisability", "Calculated remaining: ${remainingDurationMs}ms")
                        Log.d("LocomotorDisability", "Remaining time: ${remainingMinutes}m ${remainingSeconds}s")
                        Log.d("LocomotorDisability", "Starting timer with duration: ${remainingDurationMs}ms")
                        
                        Log.d("LocomotorDisability", "Restored connection to device ${activeConnection.deviceID} with ${remainingMinutes}m ${remainingSeconds}s remaining")
                        
                        // Start the timer with remaining time
                        startConnectionTimer(remainingDurationMs)
                        updateConnectionStatusUI()
                        
                        // Restore BLE connection for Line Follower
                        startBLEConnection(activeConnection.deviceID)
                        
                        Toast.makeText(this@LocomotorDisabilityActivity, 
                            "Reconnected to wheelchair: ${activeConnection.deviceID} (${remainingMinutes}m ${remainingSeconds}s left)", 
                            Toast.LENGTH_LONG).show()
                    } else {
                        // Connection expired while app was not running, clean it up
                        Log.d("LocomotorDisability", "=== CONNECTION EXPIRED - CLEANING UP ===")
                        Log.d("LocomotorDisability", "Found expired connection for device ${activeConnection.deviceID}, cleaning up")
                        
                        // Clean up local state
                        connectionTimer?.cancel()
                        connectionTimer = null
                        connectedDeviceID = null
                        connectionExpiryTimeMillis = null
                        
                        // Clean up database
                        withContext(Dispatchers.IO) {
                            MySQLHelper.updateDeviceConnectionStatus(activeConnection.deviceID, null, null)
                        }
                        
                        updateConnectionStatusUI()
                        
                        Toast.makeText(this@LocomotorDisabilityActivity, 
                            "Previous wheelchair connection expired", 
                            Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("LocomotorDisability", "=== NO CONNECTION FOUND ===")
                    Log.d("LocomotorDisability", "No active connection found in database for user $currentUserID")
                    
                    // Clean up local state if no database connection exists
                    if (connectedDeviceID != null) {
                        Log.d("LocomotorDisability", "Cleaning up orphaned local connection state")
                        connectionTimer?.cancel()
                        connectionTimer = null
                        connectedDeviceID = null
                        connectionExpiryTimeMillis = null
                    }
                    
                    // Update UI to show not connected state
                    updateConnectionStatusUI()
                }
            } catch (e: Exception) {
                Log.e("LocomotorDisability", "=== ERROR IN DATABASE RESTORATION ===")
                Log.e("LocomotorDisability", "Error restoring connection from database: ${e.message}", e)
                
                // Update UI to show not connected state on error
                updateConnectionStatusUI()
                
                Toast.makeText(this@LocomotorDisabilityActivity, 
                    "Error checking wheelchair connection status", 
                    Toast.LENGTH_SHORT).show()
            } finally {
                isRestoringConnection = false
            }
        }
    }

    private fun initializeBLE() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null) {
            Log.e("BLE", "Bluetooth not supported on this device")
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            return
        }
        
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        // Check and request permissions
        checkBluetoothPermissions()
    }

    private fun setupLineFollowerToggle() {
        lineFollowerToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isBLEConnected) {
                val command = if (isChecked) "LF_ON" else "LF_OFF"
                sendBLECommand(command)
                updateLineFollowerStatus(isChecked)
            } else {
                // Revert toggle if not connected
                lineFollowerToggle.isChecked = false
                Toast.makeText(this, "Bluetooth not connected to wheelchair", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkBluetoothPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        }
    }

    private fun startBLEConnection(deviceID: String) {
        // Store authorized device ID from QR scan
        authorizedDeviceID = deviceID
        targetBLEDeviceName = "WC_$deviceID"
        autoReconnectEnabled = true
        intentionalDisconnect = false
        
        Log.d("BLE", "=== STARTING BLE CONNECTION ===")
        Log.d("BLE", "Authorized to connect to: $targetBLEDeviceName")
        Log.d("BLE", "Device ID: $deviceID")
        
        if (!bluetoothAdapter?.isEnabled!!) {
            Log.w("BLE", "Bluetooth is not enabled")
            Toast.makeText(this, "Please enable Bluetooth to control Line Follower", Toast.LENGTH_LONG).show()
            return
        }
        
        // Start scanning for the authorized wheelchair
        Toast.makeText(this, "Searching for wheelchair Bluetooth...", Toast.LENGTH_SHORT).show()
        startBLEScan()
        
        // Also start floor detection scanning
        startFloorDetectionScan()
    }

    private fun startBLEScan() {
        if (isScanning) return
        
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            checkBluetoothPermissions()
            return
        }
        
        isScanning = true
        Log.d("BLE", "Starting BLE scan for: $targetBLEDeviceName")
        
        bluetoothLeScanner?.startScan(bleScanCallback)
        
        // Stop scanning after timeout
        Handler(Looper.getMainLooper()).postDelayed({
            stopBLEScan()
            if (!isBLEConnected) {
                Log.w("BLE", "BLE scan timeout - device not found")
                Toast.makeText(this, "Wheelchair Bluetooth not found. Please ensure wheelchair is powered on.", Toast.LENGTH_LONG).show()
            }
        }, BLE_SCAN_TIMEOUT)
    }

    private fun stopBLEScan() {
        if (!isScanning) return
        
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        isScanning = false
        bluetoothLeScanner?.stopScan(bleScanCallback)
        Log.d("BLE", "BLE scan stopped")
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (ActivityCompat.checkSelfPermission(this@LocomotorDisabilityActivity, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            
            val deviceName = result.device.name
            Log.d("BLE", "Found BLE device: $deviceName")
            
            // Check if this is the authorized wheelchair
            if (deviceName == targetBLEDeviceName && authorizedDeviceID != null) {
                Log.d("BLE", "Authorized wheelchair found: $deviceName")
                stopBLEScan()
                connectToGatt(result.device)
            }
            // Check if this is a floor beacon
            else if (deviceName?.startsWith("FLOOR_") == true) {
                handleFloorBeaconDetection(deviceName, result.rssi)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE", "BLE scan failed with error: $errorCode")
            isScanning = false
            Toast.makeText(this@LocomotorDisabilityActivity, "Bluetooth scan failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToGatt(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        Log.d("BLE", "Connecting to GATT server...")
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d("BLE", "Connected to GATT server")
                    if (ActivityCompat.checkSelfPermission(this@LocomotorDisabilityActivity, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("BLE", "Disconnected from GATT server")
                    runOnUiThread {
                        handleBLEDisconnection()
                    }
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    bleCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    if (bleCharacteristic != null) {
                        Log.d("BLE", "BLE service and characteristic found")
                        runOnUiThread {
                            onBLEConnected()
                        }
                    } else {
                        Log.e("BLE", "Characteristic not found")
                    }
                } else {
                    Log.e("BLE", "Service not found")
                }
            }
        }
    }

    private fun onBLEConnected() {
        isBLEConnected = true
        lineFollowerCard.visibility = android.view.View.VISIBLE
        lineFollowerToggle.isEnabled = true
        updateLineFollowerStatus(false) // Default to OFF
        Log.d("BLE", "BLE connected successfully")
        Toast.makeText(this, "Bluetooth connected - Line Follower available", Toast.LENGTH_SHORT).show()
    }

    private fun handleBLEDisconnection() {
        isBLEConnected = false
        lineFollowerToggle.isChecked = false
        lineFollowerToggle.isEnabled = false
        updateLineFollowerStatus(false)
        
        if (intentionalDisconnect) {
            Log.d("BLE", "Intentional BLE disconnect")
            lineFollowerCard.visibility = android.view.View.GONE
        } else if (autoReconnectEnabled) {
            Log.d("BLE", "Unexpected BLE disconnect - attempting reconnection")
            Toast.makeText(this, "Bluetooth connection lost, reconnecting...", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                if (targetBLEDeviceName != null && autoReconnectEnabled) {
                    startBLEScan()
                }
            }, 2000) // Retry after 2 seconds
        }
    }

    private fun sendBLECommand(command: String) {
        if (!isBLEConnected || bleCharacteristic == null) {
            Log.w("BLE", "Cannot send command - BLE not connected")
            return
        }
        
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        try {
            bleCharacteristic?.value = command.toByteArray()
            val success = bluetoothGatt?.writeCharacteristic(bleCharacteristic)
            Log.d("BLE", "Sent command: $command, success: $success")
        } catch (e: Exception) {
            Log.e("BLE", "Error sending BLE command: ${e.message}")
            Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLineFollowerStatus(isOn: Boolean) {
        lineFollowerStatus.text = if (isOn) {
            "IR sensors active - Line following enabled"
        } else {
            "IR sensors disabled - Manual control"
        }
    }

    private fun disconnectBLE() {
        intentionalDisconnect = true
        autoReconnectEnabled = false
        authorizedDeviceID = null // Clear authorization
        
        // Send LF_OFF command before disconnecting
        if (isBLEConnected && lineFollowerToggle.isChecked) {
            sendBLECommand("LF_OFF")
            Handler(Looper.getMainLooper()).postDelayed({
                performBLEDisconnect()
            }, 100) // Wait for command to be sent
        } else {
            performBLEDisconnect()
        }
    }

    private fun performBLEDisconnect() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        bleCharacteristic = null
        targetBLEDeviceName = null
        
        lineFollowerCard.visibility = android.view.View.GONE
        Log.d("BLE", "BLE disconnected completely")
    }

    private fun startFloorDetectionScan() {
        // Start periodic floor detection scanning
        val floorScanHandler = Handler(Looper.getMainLooper())
        val floorScanRunnable = object : Runnable {
            override fun run() {
                if (connectedDeviceID != null) { // Only scan if connected to wheelchair
                    scanForFloorBeacons()
                    floorScanHandler.postDelayed(this, FLOOR_SCAN_INTERVAL)
                }
            }
        }
        floorScanHandler.post(floorScanRunnable)
    }

    private fun scanForFloorBeacons() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        Log.d("FloorDetection", "Scanning for floor beacons...")
        
        // Use a separate scan for floor detection (shorter duration)
        bluetoothLeScanner?.startScan(floorBeaconCallback)
        
        // Stop floor beacon scan after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            bluetoothLeScanner?.stopScan(floorBeaconCallback)
        }, 5000)
    }

    private val floorBeaconCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (ActivityCompat.checkSelfPermission(this@LocomotorDisabilityActivity, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            
            val deviceName = result.device.name
            if (deviceName?.startsWith("FLOOR_") == true) {
                handleFloorBeaconDetection(deviceName, result.rssi)
            }
        }
    }

    private fun handleFloorBeaconDetection(beaconName: String, rssi: Int) {
        // Extract floor number from beacon name (e.g., "FLOOR_1" -> "1")
        val floorNumber = beaconName.substringAfter("FLOOR_")
        
        // Only update if signal is strong enough (closer than -70 dBm)
        if (rssi > -70) {
            if (currentFloor != floorNumber) {
                currentFloor = floorNumber
                Log.d("FloorDetection", "User is now on floor: $floorNumber (RSSI: $rssi)")
                
                // Update UI to show current floor
                runOnUiThread {
                    updateFloorDisplay(floorNumber)
                }
                
                // Optionally update database with user's current floor
                updateUserFloorInDatabase(floorNumber)
            }
        }
    }

    private fun updateFloorDisplay(floor: String) {
        // Update connection status to include floor information
        if (connectedDeviceID != null) {
            connectionStatusTextView.text = "Connected to Wheelchair: $connectedDeviceID\nCurrent Floor: $floor"
        }
    }

    private fun updateUserFloorInDatabase(floor: String) {
        if (currentUserID != null) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        // TODO: Add updateUserCurrentFloor method to MySQLHelper if needed
                        // For now, just log the floor detection
                        Log.d("FloorDetection", "User $currentUserID detected on floor: $floor")
                        // MySQLHelper.updateUserCurrentFloor(currentUserID!!, floor)
                    } catch (e: Exception) {
                        Log.e("FloorDetection", "Failed to update floor in database: ${e.message}")
                    }
                }
            }
        }
    }

    private fun checkDatabaseStatus(deviceID: String) {
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                MySQLHelper.getDeviceStatus(deviceID)
            }
            Log.d("DatabaseCheck", "=== DATABASE STATUS CHECK ===")
            Log.d("DatabaseCheck", "Device $deviceID current status: $status")
            
            // Also check if there's an active connection for current user
            val activeConnection = withContext(Dispatchers.IO) {
                MySQLHelper.getActiveConnectionForUser(currentUserID ?: "")
            }
            Log.d("DatabaseCheck", "Active connection for user $currentUserID: ${activeConnection?.deviceID}")
        }
    }

    // Method to manually test BLE reconnection
    private fun testBLEReconnection() {
        if (connectedDeviceID != null && !isBLEConnected) {
            Log.d("BLE", "=== MANUAL BLE RECONNECTION TEST ===")
            Log.d("BLE", "Attempting to reconnect to: $connectedDeviceID")
            startBLEConnection(connectedDeviceID!!)
        } else {
            Log.d("BLE", "Cannot test BLE reconnection - connectedDeviceID: $connectedDeviceID, isBLEConnected: $isBLEConnected")
        }
    }
}
