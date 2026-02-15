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
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

import androidx.drawerlayout.widget.DrawerLayout
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
    private lateinit var connectionStatusTextView: TextView



    private var connectedDeviceID: String? = null
    private var connectionTimer: CountDownTimer? = null
    private var currentConnectionDurationMs: Long = 30 * 60 * 1000 // Default 30 minutes
    private var connectionExpiryTimeMillis: Long? = null
    private var isRestoringConnection = false // Flag to prevent multiple restoration attempts

    //assistant button counter
    private lateinit var assistanceButton: Button
    private var countDownTimer: CountDownTimer? = null

    private val animationStages = listOf(
        1 to 200,
        2 to 220,
        3 to 240,
        4 to 260,
        5 to 286,
    )

    enum class DeviceStatus {
        AVAILABLE,
        IN_USE,
        MAINTENANCE,
        UNKNOWN
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

    private fun restoreConnectionState(deviceID: String, expiryTime: Long) {
        Log.d("Connection", "Restoring state for device: $deviceID, Expiry: $expiryTime")

        // Set the global connection variables
        connectedDeviceID = deviceID
        connectionExpiryTimeMillis = expiryTime

        val remainingTime = expiryTime - System.currentTimeMillis()

        if (remainingTime > 0) {
            // If there's time left, start the timer and update the UI
            startConnectionTimer(remainingTime)
            updateConnectionStatusUI() // This function will now correctly update the text
        } else {
            // If time has already expired, log it and perform a disconnect
            Log.w("Connection", "Restored connection for $deviceID had already expired. Disconnecting.")
            disconnectFromDevice(showToast = false) // Disconnect without showing a toast
        }
    }

    private fun showDurationSelectionDialog(deviceID: String) {
        val durationOptions = arrayOf("30 minutes", "1 hour", "2 hours", "4 hours")
        // Corresponding values in milliseconds
        val durationValuesMs = longArrayOf(30 * 60 * 1000, 60 * 60 * 1000, 2 * 60 * 60 * 1000, 4 * 60 * 60 * 1000)

        AlertDialog.Builder(this)
            .setTitle("Select Connection Duration")
            .setItems(durationOptions) { dialog, which ->
                val selectedDurationMs = durationValuesMs[which]
                Log.d("LocomotorDisability", "User selected duration: ${durationOptions[which]} ($selectedDurationMs ms) for device $deviceID")
                // Call your existing, powerful connectToDevice function
                connectToDevice(deviceID, selectedDurationMs)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Connection cancelled.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }



    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_locomotor_disability)

        // --- GROUP 1: INITIALIZE VIEWS & DATA ---
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        connectionStatusTextView = findViewById(R.id.connection_status_textview)
        assistanceButton = findViewById(R.id.assistance_button)

        resetRunnable = Runnable {
            Log.d("LocomotorDisability", "30-minute timer elapsed. Forcing button reset.")
            forceResetButtonState()
        }

        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserID = sharedPreferences.getString("userID", null)
        currentUserFullName = sharedPreferences.getString("fullName", "User")

        UserSingleton.userID = currentUserID
        UserSingleton.fullName = currentUserFullName

        Log.d("LocomotorDisability", "=== APP STARTING | User: $currentUserID ===")

        // --- GROUP 2: SET UP LISTENERS & NON-DB UI ---
        setupHoldToActivateButton()
        initializeUserName() // Assuming this function only sets text from variables

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    true
                }
                R.id.nav_scan_qr -> {
                    if (connectedDeviceID == null) {
                        launchQrScanner()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Disconnect Wheelchair")
                            .setMessage("Do you want to disconnect from $connectedDeviceID?")
                            .setPositiveButton("Yes") { _, _ -> disconnectFromDevice() }
                            .setNegativeButton("No", null)
                            .show()
                    }
                    true
                }
                R.id.nav_notifications -> {
                    true
                }
                R.id.nav_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }

        // --- GROUP 3: ASYNCHRONOUS DATABASE OPERATIONS ---
        val userId = currentUserID
        if (userId != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                MySQLHelper.awaitInitialized()
                Log.d("LocomotorDisability", "MySQLHelper is initialized. Proceeding with DB operations.")

                val connectionInfo: ActiveConnectionInfo? = MySQLHelper.getActiveConnectionForUser(userId)

                withContext(Dispatchers.Main) {
                    if (connectionInfo != null) {
                        Log.d("Connection", "Restoring active connection for device: ${connectionInfo.deviceID}")
                        restoreConnectionState(connectionInfo.deviceID, connectionInfo.expiryTime)
                    }
                    else {
                        Log.d("Connection", "No active connection found for user $userId.")
                        updateConnectionStatusUI()
                    }
                }

                val cleanedUp = MySQLHelper.cleanupExpiredConnections()
                if (cleanedUp > 0) {
                    Log.d("LocomotorDisability", "Cleaned up $cleanedUp expired connections.")
                }
            }
        } else {
            Log.e("LocomotorDisability", "Cannot perform DB operations: UserID is null.")
        }

        // Registering for FCM does not depend on the DB, so it can be here.
        registerFCMToken()
    }


    private var isAlertSent = false
    private var alertTimestamp = 0L

    private val resetHandler = Handler(Looper.getMainLooper())
    private lateinit var resetRunnable: Runnable

    private fun onPushNotificationReceived(action: String) {
        if (action == "incident_resolved") {
            runOnUiThread {
                Log.d("Locomotor Disability", "Incident resolved externally. Resetting button.")
                forceResetButtonState()
            }
        }
    }

    private fun launchQrScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan QR Code on Wheelchair")
        options.setCameraId(0)
        options.setBeepEnabled(true)
        options.setBarcodeImageEnabled(true)
        options.setOrientationLocked(true)
        qrCodeScannerLauncher.launch(options)
    }


    private fun setupHoldToActivateButton() {
        assistanceButton.setOnTouchListener { _,  event ->
            if (isAlertSent) return@setOnTouchListener true

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startHoldAnimation()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelHoldAnimation()
                    true // Changed to true to indicate the event was handled
                }
                else -> false
            }
        }
    }

    private fun startHoldAnimation() {
        countDownTimer = object : CountDownTimer(5100, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsPassed = 5 - (millisUntilFinished / 1000)
                if (secondsPassed in 1..animationStages.size) {
                    val (count, sizeDp) = animationStages[secondsPassed.toInt() - 1]
                    assistanceButton.text = count.toString()
                    animateButtonSize(dpToPx(sizeDp))
                }
            }

            override fun onFinish() {
                isAlertSent = true
                assistanceButton.text = "5"
                animateButtonSize(dpToPx(animationStages.last().second))
                sendEmergencyAlert()
                playSuccessAnimation()

                alertTimestamp = System.currentTimeMillis()

                resetHandler.postDelayed(resetRunnable, 30 * 60 * 1000)
            }
        }.start()
    }

    private fun cancelHoldAnimation() {
        if (!isAlertSent) {
            countDownTimer?.cancel()
            resetToInitialState()
        }
    }

    private fun forceResetButtonState() {
        resetHandler.removeCallbacks(resetRunnable)
        assistanceButton.clearAnimation()
        assistanceButton.scaleX = 1.0f
        assistanceButton.scaleY = 1.0f

        isAlertSent = false
        alertTimestamp = 0L
        assistanceButton.text = "SOS"
        assistanceButton.textSize = 34f
        animateButtonSize(dpToPx(174))

        Toast.makeText(this, "Assistance button has been reset.", Toast.LENGTH_LONG).show()
    }
    private fun resetToInitialState() {
        animateButtonSize(dpToPx(174))
        val currentCount = assistanceButton.text.toString().toIntOrNull() ?: 1
        val resetDuration = (currentCount * 150).toLong()

        object : CountDownTimer(resetDuration, 150) {
            override fun onTick(millisUntilFinished: Long) {
                val count = (millisUntilFinished /150).toInt()
                if (count > 0) {
                    assistanceButton.text = count.toString()
                }
            }

            override fun onFinish() {
                assistanceButton.text = "SOS"
            }
        }.start()
    }

    private fun playSuccessAnimation() {
        val animator = ValueAnimator.ofFloat(1.0f, 1.05f)
        animator.duration = 800
        animator.repeatMode = ValueAnimator.REVERSE
        animator.repeatCount = ValueAnimator.INFINITE

        animator.addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            assistanceButton.scaleX = scale
            assistanceButton.scaleY = scale
        }
        assistanceButton.text = "Help is on the way"
        assistanceButton.textSize = 24f
        animator.start()
    }

    private fun animateButtonSize(newSizePx: Int) {
        val button = assistanceButton
        val animator = ValueAnimator.ofInt(button.width, newSizePx)
        animator.duration = 200

        animator.addUpdateListener {animation ->
            val value = animation.animatedValue as Int
            val layoutParams: ViewGroup.LayoutParams = button.layoutParams
            layoutParams.width = value
            layoutParams.height = value
            button.layoutParams = layoutParams
        }
        animator.start()
    }

    private fun sendEmergencyAlert() {
        val uid = currentUserID
        val name = currentUserFullName
        val devId = connectedDeviceID

        if (uid != null && name != null) {
            if (devId != null) {
                // This triggers the actual database insertion
                requestAssistanceWithDevice(devId, uid, name)
            } else {
                // DISCUSSION: User is NOT connected to wheelchair.
                // For now, we show a message. You can add phone GPS logic here later.
                Toast.makeText(this, "Emergency alert sent using phone location!", Toast.LENGTH_LONG).show()
                Log.d("Assistance", "Manual SOS triggered without wheelchair connection.")
            }
        } else {
            Toast.makeText(this, "Error: User details missing. Please log in again.", Toast.LENGTH_LONG).show()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
                // In connectToDevice()
                val deviceStatus: DeviceStatus = withContext(Dispatchers.IO) {
                    MySQLHelper.getDeviceStatus(deviceID)
                }

                val errorMessage = when (deviceStatus) {
                    DeviceStatus.MAINTENANCE -> "Wheelchair $deviceID is currently under maintenance..."
                    DeviceStatus.IN_USE -> "Wheelchair $deviceID is currently in use by another user..."
                    else -> "Wheelchair $deviceID is currently unavailable..."
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



            } else {
                Toast.makeText(this@LocomotorDisabilityActivity, "Failed to connect to wheelchair $deviceID. It might be in use or an error occurred.", Toast.LENGTH_LONG).show()
                Log.e("DeviceConnect", "Failed to connect to device $deviceID - database update failed")
                connectionExpiryTimeMillis = null // Reset if connection failed
            }
        }
    }

    private fun disconnectFromDevice(showToast: Boolean = true) {
        val deviceToDisconnect = connectedDeviceID
        if (deviceToDisconnect == null) {
            Log.w("DeviceConnect", "Nothing to disconnect.")
            return
        }

        // 1. Stop the countdown timer
        connectionTimer?.cancel()
        connectionTimer = null

        // 2. Update the Database
        lifecycleScope.launch(Dispatchers.IO) {
            // We pass null for UserID and null for Expiry to reset the wheelchair to "available"
            val success = MySQLHelper.updateDeviceConnectionStatus(deviceToDisconnect, null, null)

            withContext(Dispatchers.Main) {
                if (success) {
                    // 3. Clear local state only after DB confirms success
                    connectedDeviceID = null
                    connectionExpiryTimeMillis = null

                    // 4. Update the UI (This resets the Bottom Nav text to "Scan QR")
                    updateConnectionStatusUI()

                    if (showToast) {
                        Toast.makeText(this@LocomotorDisabilityActivity, "Disconnected from wheelchair.", Toast.LENGTH_SHORT).show()
                    }
                    Log.d("DeviceConnect", "Successfully disconnected device: $deviceToDisconnect")
                } else {
                    Toast.makeText(this@LocomotorDisabilityActivity, "Disconnection failed on server.", Toast.LENGTH_LONG).show()
                    Log.e("DeviceConnect", "Database update failed for disconnect.")
                }
            }
        }
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
        if (!::bottomNavigationView.isInitialized) return
        // 1. Get the menu item from the bottom navigation
        val scanItem = bottomNavigationView.menu.findItem(R.id.nav_scan_qr)

        if (connectedDeviceID != null) {
            // State: CONNECTED
            connectionStatusTextView.text = "Connected to Wheelchair: $connectedDeviceID"
            connectionStatusTextView.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Green

            // 2. Change the 2nd icon's text to "Disconnect"
            scanItem.title = "Disconnect"
        } else {
            // State: NOT CONNECTED
            connectionStatusTextView.text = "Not connected to a wheelchair."
            connectionStatusTextView.setTextColor(android.graphics.Color.parseColor("#8C8C8C")) // Gray

            // 3. Change the 2nd icon's text back to "Scan QR"
            scanItem.title = "Scan QR"
        }
    }

    private fun requestAssistanceWithDevice(deviceID: String, userID: String, fullName: String) {
        lifecycleScope.launch {
            val userFullName = withContext(Dispatchers.IO) {
                try { MySQLHelper.getUserFullNameByUserID(userID) } catch (e: Exception) { null }
            } ?: fullName

            val deviceLocation: MySQLHelper.DeviceLocation? = withContext(Dispatchers.IO) {
                MySQLHelper.getDeviceLastLocation(deviceID)
            }

            // FALLBACK LOGIC:
            // Even if deviceLocation is null, we define default values so the SOS still goes through
            val lat = deviceLocation?.latitude ?: 0.0
            val lng = deviceLocation?.longitude ?: 0.0
            val floor = deviceLocation?.floorLevel ?: "Location Unknown"

            Log.d("LocomotorDisability", "Sending Alert for $userFullName. Device info found: ${deviceLocation != null}")

            val success = withContext(Dispatchers.IO) {
                MySQLHelper.insertAssistanceRequestFromDevice(
                    this@LocomotorDisabilityActivity,
                    userID,
                    userFullName,
                    deviceID,
                    lat,
                    lng,
                    floor
                )
            }

            if (success) {
                Toast.makeText(this@LocomotorDisabilityActivity, "SOS Sent! Help is on the way.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@LocomotorDisabilityActivity, "Server Error: Could not send alert.", Toast.LENGTH_LONG).show()
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
        }

        restoreConnectionFromDatabase()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionTimer?.cancel()

        resetHandler.removeCallbacks(resetRunnable)
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
        findViewById<TextView>(R.id.user_fullname)?.text = "$fullName!"

        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = fullName
        }
    }

    private fun showDeviceLocationOnMap(deviceID: String) {
        lifecycleScope.launch {
            val deviceLocation: MySQLHelper.DeviceLocation? = withContext(Dispatchers.IO) { // <-- Use the data class
                MySQLHelper.getDeviceLastLocation(deviceID)
            }

            if (deviceLocation?.latitude != null && deviceLocation.longitude != null) { // <-- Safe check
                val intent = Intent(this@LocomotorDisabilityActivity, MapActivity::class.java).apply {
                    putExtra("EXTRA_LATITUDE", deviceLocation.latitude)
                    putExtra("EXTRA_LONGITUDE", deviceLocation.longitude)
                    putExtra("EXTRA_DEVICE_NAME", deviceLocation.name ?: "Wheelchair Location")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this@LocomotorDisabilityActivity, "Wheelchair location is not available.", Toast.LENGTH_LONG).show()
            }
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
                    val deviceStatus: DeviceStatus = MySQLHelper.getDeviceStatus("202501")
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
                    Log.d("LocomotorDisability", "Connection expires: ${activeConnection.expiryTime}")
                    Log.d("LocomotorDisability", "Time difference: ${activeConnection.expiryTime - currentTimeMillis}ms")

                    if (activeConnection.expiryTime > currentTimeMillis) {
                        // Connection is still valid, restore it
                        Log.d("LocomotorDisability", "=== RESTORING CONNECTION ===")

                        // Cancel any existing timer to prevent duplicates
                        connectionTimer?.cancel()
                        connectionTimer = null

                        // Restore connection state
                        connectedDeviceID = activeConnection.deviceID
                        connectionExpiryTimeMillis = activeConnection.expiryTime



                        val remainingDurationMs = activeConnection.expiryTime - currentTimeMillis
                        val remainingMinutes = remainingDurationMs / (60 * 1000)
                        val remainingSeconds = (remainingDurationMs % (60 * 1000)) / 1000

                        Log.d("LocomotorDisability", "=== TIMING DETAILS ===")
                        Log.d("LocomotorDisability", "Database expiry time: ${activeConnection.expiryTime}")
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



}
