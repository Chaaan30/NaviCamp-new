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
import android.Manifest // New: For Bluetooth permissions
import android.bluetooth.BluetoothAdapter // New: For Bluetooth operations
import android.bluetooth.BluetoothManager // New: For Bluetooth operations
import android.bluetooth.BluetoothGatt // New: For GATT connection
import android.bluetooth.BluetoothGattCallback // New: For GATT callbacks
import android.bluetooth.BluetoothProfile // New: For GATT connection state
import android.bluetooth.le.ScanCallback // New: For BLE scanning
import android.bluetooth.le.ScanResult // New: For BLE scanning results
import android.content.pm.PackageManager // New: For permission checks
import android.os.Build // New: For API level checks
import java.util.UUID // New: For BLE UUIDs
import androidx.activity.result.contract.ActivityResultContracts // New: For permission launcher
import androidx.core.app.ActivityCompat // New: For permission checks
import androidx.core.content.ContextCompat // New: For permission checks
import android.widget.ToggleButton // New: Import ToggleButton
import android.widget.CompoundButton // New: Import for OnCheckedChangeListener

class LocomotorDisabilityActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var assistanceButton: Button
    private lateinit var assistanceConnectButton: Button
    private lateinit var connectionStatusTextView: TextView

    // New: UI elements for Line Follower Control
    private lateinit var lineFollowerConnectButton: Button
    private lateinit var lineFollowerToggleButton: ToggleButton
    private lateinit var bleStatusTextView: TextView

    // New: BLE related variables
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var lineFollowerCharacteristicUuid: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private var bleConnectedToLineFollower: Boolean = false
    private var targetDeviceName: String = "WC_202501" // Main wheelchair device name

    // New: Define BLE Service UUID
    private val SERVICE_UUID: String = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"

    private var connectedDeviceID: String? = null
    private var connectionTimer: CountDownTimer? = null
    private var currentConnectionDurationMs: Long = 30 * 60 * 1000 // Default 30 minutes
    private var connectionExpiryTimeMillis: Long? = null
    private var isRestoringConnection = false // Flag to prevent multiple restoration attempts

    // New: Request code for enabling Bluetooth
    private val REQUEST_ENABLE_BT = 1

    // New: Permission request launcher
    private val requestBluetoothPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d("BLE", "All required Bluetooth permissions granted.")
            // Permissions granted, can now try to enable BT or start scan
            ensureBluetoothEnabled()
        } else {
            Log.w("BLE", "Not all Bluetooth permissions granted. BLE functionality may be limited.")
            Toast.makeText(this, "Bluetooth permissions denied. Cannot use line follower control.", Toast.LENGTH_LONG).show()
            updateBleStatusUI("Permissions denied")
        }
    }

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

    // New: BLE Scan Callback
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                if (device.name == targetDeviceName) {
                    Log.d("BLE_Scan", "Found target device: ${device.name} (${device.address})")
                    stopBleScan() // Stop scanning once found
                    connectToLineFollowerBle(device.address) // Connect to the device
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            // Handle batch results if needed, for now just individual scan results
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BLE_Scan", "BLE scan failed with error code: $errorCode")
            Toast.makeText(this@LocomotorDisabilityActivity, "BLE scan failed. Code: $errorCode", Toast.LENGTH_LONG).show()
            updateBleStatusUI("Scan failed")
            lineFollowerConnectButton.isEnabled = true
        }
    }

    // New: BLE GATT Callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val deviceAddress = gatt?.device?.address ?: "Unknown Device"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BLE_GATT", "Successfully connected to GATT client: $deviceAddress")
                    bluetoothGatt = gatt
                    runOnUiThread { updateBleStatusUI("Connected to: $targetDeviceName") }
                    // Discover services after successful connection
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BLE_GATT", "Disconnected from GATT client: $deviceAddress")
                    runOnUiThread { disconnectBleLineFollower() }
                }
            } else {
                Log.e("BLE_GATT", "GATT connection failed with status: $status for device: $deviceAddress")
                runOnUiThread { disconnectBleLineFollower(showToast = true) }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE_GATT", "Services discovered for ${gatt?.device?.address}")
                val service = gatt?.getService(UUID.fromString(SERVICE_UUID))
                val serviceUuidString = SERVICE_UUID.lowercase(Locale.ROOT)
                Log.d("BLE_GATT", "Looking for service with UUID: $serviceUuidString")

                gatt?.services?.forEach { s ->
                    Log.d("BLE_GATT", "Found service: ${s.uuid.toString().lowercase(Locale.ROOT)}")
                }

                if (service != null) {
                    val characteristic = service.getCharacteristic(lineFollowerCharacteristicUuid)
                    if (characteristic != null) {
                        Log.d("BLE_GATT", "Found Line Follower Characteristic: ${lineFollowerCharacteristicUuid}")
                        bleConnectedToLineFollower = true
                        runOnUiThread { lineFollowerToggleButton.isEnabled = true }
                        runOnUiThread { Toast.makeText(this@LocomotorDisabilityActivity, "Line Follower control ready!", Toast.LENGTH_SHORT).show() }
                    } else {
                        Log.e("BLE_GATT", "Line Follower Characteristic not found for UUID: ${lineFollowerCharacteristicUuid}")
                        runOnUiThread { disconnectBleLineFollower(showToast = true); updateBleStatusUI("Char not found") }
                    }
                } else {
                    Log.e("BLE_GATT", "Service with UUID ${SERVICE_UUID} not found.")
                    runOnUiThread { disconnectBleLineFollower(showToast = true); updateBleStatusUI("Service not found") }
                }
            } else {
                Log.e("BLE_GATT", "onServicesDiscovered received status: $status")
                runOnUiThread { disconnectBleLineFollower(showToast = true); updateBleStatusUI("Service discovery failed") }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: android.bluetooth.BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE_GATT", "Characteristic write successful: ${characteristic?.uuid}")
            } else {
                Log.e("BLE_GATT", "Characteristic write failed: ${characteristic?.uuid}, Status: $status")
                runOnUiThread { Toast.makeText(this@LocomotorDisabilityActivity, "Failed to send command.", Toast.LENGTH_SHORT).show() }
            }
        }
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

        // New: Initialize Line Follower UI elements with explicit types
        lineFollowerConnectButton = findViewById<Button>(R.id.line_follower_connect_button)
        lineFollowerToggleButton = findViewById<ToggleButton>(R.id.line_follower_toggle_button)
        bleStatusTextView = findViewById<TextView>(R.id.ble_status_textview)

        // New: Initialize BluetoothAdapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

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
                    val loggedOutUserID = sharedPreferences.getString("userID", null)
                    editor.clear()
                    editor.apply()

                    // Clear FCM token from database asynchronously
                    if (loggedOutUserID != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                MySQLHelper.clearUserFCMToken(loggedOutUserID)
                                Log.d("LocomotorDisability", "FCM token cleared for user: $loggedOutUserID on logout")
                            } catch (e: Exception) {
                                Log.e("LocomotorDisability", "Error clearing FCM token on logout: ${e.message}", e)
                            }
                        }
                    }

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

        // New: Set up the Line Follower Connect Button
        lineFollowerConnectButton.setOnClickListener { 
            if (connectedDeviceID == null) {
                Toast.makeText(this, "Please connect to a wheelchair via QR Code first.", Toast.LENGTH_LONG).show()
                Log.w("BLE", "Cannot connect BLE, no database connection to wheelchair.")
                return@setOnClickListener
            }

            // Request BLE permissions and enable Bluetooth only when the button is clicked
            checkBlePermissions()

            if (!bleConnectedToLineFollower) {
                // Attempt to connect to BLE Line Follower
                Log.d("BLE", "Attempting to connect to Line Follower BLE...")
                lineFollowerConnectButton.isEnabled = false // Disable while connecting
                updateBleStatusUI("Scanning...")
                startBleScan()
            } else {
                // Disconnect BLE Line Follower
                Log.d("BLE", "Disconnecting from Line Follower BLE...")
                disconnectBleLineFollower()
            }
        }

        // New: Set up the Line Follower Toggle Button
        lineFollowerToggleButton.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            if (bleConnectedToLineFollower) {
                val command = if (isChecked) "LF_ON" else "LF_OFF"
                writeBleCommand(command)
                Log.d("BLE", "Sending command: $command")
            } else {
                Toast.makeText(this, "Not connected to Line Follower BLE.", Toast.LENGTH_SHORT).show()
                lineFollowerToggleButton.isChecked = false // Revert toggle state
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

                    // Capture currentUserID in an immutable variable
                    val userIdToUse = currentUserID

                    if (userIdToUse != null && token != null) {
                        // Before updating the current user's token, clear this token from any other users
                        lifecycleScope.launch(Dispatchers.IO) {
                            MySQLHelper.clearFCMTokenFromOtherUsers(token, userIdToUse)
                        }

                        // Update token in database
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val success = MySQLHelper.updateUserFCMToken(userIdToUse, token)
                                if (success) {
                                    Log.d("LocomotorDisability", "FCM token updated successfully for user: $userIdToUse")
                                } else {
                                    Log.e("LocomotorDisability", "Failed to update FCM token for user: $userIdToUse")
                                }
                            } catch (e: Exception) {
                                Log.e("LocomotorDisability", "Error updating FCM token: ${e.message}", e)
                            }
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

                // Fetch deviceName from database and update targetDeviceName
                val fetchedDeviceName = withContext(Dispatchers.IO) {
                    MySQLHelper.getDeviceNameById(deviceID)
                }

                if (fetchedDeviceName != null) {
                    targetDeviceName = fetchedDeviceName
                    Log.d("DeviceConnect", "Updated targetDeviceName to: $targetDeviceName for BLE scan.")
                } else {
                    Log.w("DeviceConnect", "Could not retrieve deviceName for $deviceID from DB. Using default: $targetDeviceName")
                    // Keep the hardcoded default if DB lookup fails, but log a warning
                }

                val durationMinutes = currentConnectionDurationMs / (60 * 1000)
                Toast.makeText(this@LocomotorDisabilityActivity, "Successfully connected to wheelchair: $deviceID for $durationMinutes mins", Toast.LENGTH_LONG).show()
                Log.d("DeviceConnect", "Successfully connected to device $deviceID")
                startConnectionTimer(currentConnectionDurationMs) // Start timer with the chosen/active duration
                updateConnectionStatusUI()

                // After successful DB connection and getting deviceName, check BLE permissions and status
                // This will enable the lineFollowerConnectButton if everything is ready
                checkBlePermissions()

            } else {
                Toast.makeText(this@LocomotorDisabilityActivity, "Failed to connect to wheelchair $deviceID. It might be in use or an error occurred.", Toast.LENGTH_LONG).show()
                Log.e("DeviceConnect", "Failed to connect to device $deviceID - database update failed")
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

        // Also disconnect BLE line follower if connected
        disconnectBleLineFollower(showToast = false)

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
        // New: Close BluetoothGatt connection on activity destroy
        bluetoothGatt?.close()
        bluetoothGatt = null
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

                        // New: Also restore targetDeviceName for BLE if a connection is found
                        val fetchedDeviceName = withContext(Dispatchers.IO) {
                            MySQLHelper.getDeviceNameById(activeConnection.deviceID)
                        }

                        if (fetchedDeviceName != null) {
                            targetDeviceName = fetchedDeviceName
                            Log.d("LocomotorDisability", "Restored targetDeviceName to: $targetDeviceName for BLE scan.")
                        } else {
                            Log.w("LocomotorDisability", "Could not retrieve deviceName for ${activeConnection.deviceID} during restore. Using default: $targetDeviceName")
                        }
                        
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
                        
                        Toast.makeText(this@LocomotorDisabilityActivity, 
                            "Reconnected to wheelchair: ${activeConnection.deviceID} (${remainingMinutes}m ${remainingSeconds}s left)", 
                            Toast.LENGTH_LONG).show()

                        // After restoring DB connection, ensure BLE permissions and status are checked
                        checkBlePermissions()

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

    // New: Function to check and request BLE permissions
    private fun checkBlePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) and above
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE) // Optional, but good to have if advertising is ever needed
        } else {
            // Android 11 (API 30) and below, location permission is needed for BLE scans
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Check if all permissions are already granted
        val allGranted = permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

        if (!allGranted) {
            // Request permissions if not all are granted
            requestBluetoothPermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            Log.d("BLE", "All required Bluetooth permissions already granted.")
            ensureBluetoothEnabled()
        }
    }

    // New: Function to ensure Bluetooth is enabled
    private fun ensureBluetoothEnabled() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device.", Toast.LENGTH_LONG).show()
            updateBleStatusUI("Bluetooth not supported")
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            Log.d("BLE", "Prompting user to enable Bluetooth.")
        } else {
            Log.d("BLE", "Bluetooth is already enabled.")
            updateBleStatusUI("Ready to connect")
            // Enable the line follower connect button here, after permissions and BT are ready
            lineFollowerConnectButton.isEnabled = true
        }
    }

    // New: Override onActivityResult to handle Bluetooth enable request result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Log.d("BLE", "Bluetooth enabled by user.")
                updateBleStatusUI("Ready to connect")
                lineFollowerConnectButton.isEnabled = true
            } else {
                Log.w("BLE", "Bluetooth not enabled by user.")
                Toast.makeText(this, "Bluetooth must be enabled to use line follower control.", Toast.LENGTH_LONG).show()
                updateBleStatusUI("Bluetooth disabled")
                lineFollowerConnectButton.isEnabled = false
            }
        }
    }

    // New: Function to update BLE status UI
    private fun updateBleStatusUI(status: String) {
        bleStatusTextView.text = "BLE Status: $status"
        if (!bleConnectedToLineFollower) {
            lineFollowerToggleButton.isEnabled = false
            lineFollowerToggleButton.isChecked = false
        }
    }

    // New: Function to disconnect BLE Line Follower
    private fun disconnectBleLineFollower(showToast: Boolean = true) {
        bluetoothGatt?.close()
        bluetoothGatt = null
        bleConnectedToLineFollower = false
        updateBleStatusUI("Disconnected")
        lineFollowerToggleButton.isChecked = false
        lineFollowerToggleButton.isEnabled = false
        lineFollowerConnectButton.text = "Connect Line Follower"
        lineFollowerConnectButton.isEnabled = true
        if (showToast) {
            Toast.makeText(this, "Line Follower BLE disconnected.", Toast.LENGTH_SHORT).show()
        }
        Log.d("BLE", "Line Follower BLE disconnected.")
    }

    // New: Function to start BLE scan
    private fun startBleScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            ensureBluetoothEnabled()
            return // Will re-attempt scan after BT is enabled
        }

        // Check for permissions again before scanning (redundant but safe)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                checkBlePermissions() // Request permissions if missing
                return
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkBlePermissions() // Request permissions if missing
            return
        }

        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e("BLE_Scan", "Bluetooth LE Scanner not available.")
            Toast.makeText(this, "Bluetooth LE not available on device.", Toast.LENGTH_LONG).show()
            updateBleStatusUI("Scanner not available")
            lineFollowerConnectButton.isEnabled = true
            return
        }

        // Define Scan Settings (e.g., low latency)
        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Define Scan Filters (e.g., by device name)
        val scanFilter = android.bluetooth.le.ScanFilter.Builder()
            .setDeviceName(targetDeviceName)
            .build()

        val filters = listOf(scanFilter)

        bluetoothLeScanner.startScan(filters, scanSettings, bleScanCallback)
        Log.d("BLE_Scan", "Started BLE scan for device: $targetDeviceName")
        updateBleStatusUI("Scanning for $targetDeviceName...")
    }

    // New: Function to stop BLE scan
    private fun stopBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w("BLE_Scan", "Cannot stop scan, BLUETOOTH_SCAN permission missing.")
                return
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("BLE_Scan", "Cannot stop scan, ACCESS_FINE_LOCATION permission missing.")
            return
        }

        bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
        Log.d("BLE_Scan", "Stopped BLE scan.")
    }

    // New: Function to connect to a BLE device
    private fun connectToLineFollowerBle(address: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w("BLE_GATT", "BluetoothAdapter not initialized or not enabled.")
            updateBleStatusUI("BT not ready")
            lineFollowerConnectButton.isEnabled = true
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w("BLE_GATT", "Cannot connect, BLUETOOTH_CONNECT permission missing.")
                checkBlePermissions()
                return
            }
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            Log.w("BLE_GATT", "Device not found. Unable to connect.")
            Toast.makeText(this, "BLE Device not found.", Toast.LENGTH_LONG).show()
            updateBleStatusUI("Device not found")
            lineFollowerConnectButton.isEnabled = true
            return
        }

        // Connect to the GATT server hosted on the BLE device
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        Log.d("BLE_GATT", "Attempting to connect to GATT: $address")
        updateBleStatusUI("Connecting to $targetDeviceName...")
    }

    // Corrected writeBleCommand function
    private fun writeBleCommand(command: String) {
        if (bluetoothGatt == null) {
            Log.e("BLE_GATT", "BluetoothGatt not initialized.")
            Toast.makeText(this, "BLE not connected.", Toast.LENGTH_SHORT).show()
            lineFollowerToggleButton.isChecked = false
            return
        }

        val service = bluetoothGatt?.getService(UUID.fromString(SERVICE_UUID))
        if (service == null) {
            Log.e("BLE_GATT", "Service not found for UUID: ${SERVICE_UUID}")
            Toast.makeText(this, "BLE Service not found.", Toast.LENGTH_SHORT).show()
            lineFollowerToggleButton.isChecked = false
            return
        }

        val characteristic = service.getCharacteristic(lineFollowerCharacteristicUuid)
        if (characteristic == null) {
            Log.e("BLE_GATT", "Characteristic not found for UUID: ${lineFollowerCharacteristicUuid}")
            Toast.makeText(this, "BLE Characteristic not found.", Toast.LENGTH_SHORT).show()
            lineFollowerToggleButton.isChecked = false
            return
        }

        // --- THIS IS THE FIX ---
        // Explicitly set the write type to match the ESP32's PROPERTY_WRITE.
        // WRITE_TYPE_DEFAULT is an alias for "Write with Response".
        characteristic.writeType = android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        // Now, set the value and write the characteristic
        characteristic.setValue(command.toByteArray(Charsets.UTF_8))

        // Permission check for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w("BLE_GATT", "Cannot write, BLUETOOTH_CONNECT permission missing.")
                Toast.makeText(this, "BLE permissions missing to send command.", Toast.LENGTH_SHORT).show()
                lineFollowerToggleButton.isChecked = false
                return
            }
        }

        val success = bluetoothGatt?.writeCharacteristic(characteristic)
        if (success == true) {
            Log.d("BLE_GATT", "Attempted to write: $command with WRITE_TYPE_DEFAULT")
        } else {
            Log.e("BLE_GATT", "Failed to initiate characteristic write: $command")
            Toast.makeText(this, "Failed to send command.", Toast.LENGTH_SHORT).show()
            lineFollowerToggleButton.isChecked = false
        }
    }
}
