package com.capstone.navicamp

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class LocomotorDisabledHomeFragment : Fragment(R.layout.fragment_locomotor_disabled_home) {

    // UI Elements
    private lateinit var connectionStatusTextView: TextView
    private lateinit var assistanceButton: Button
    private lateinit var assistanceButtonBackground: Button
    private lateinit var expirySection: View
    private lateinit var expiryDateText: TextView
    private lateinit var expiryTitle: TextView
    private lateinit var emergencyBanner: View

    // State Variables
    private var connectedDeviceID: String? = null
    private var connectionTimer: CountDownTimer? = null
//    private var currentConnectionDurationMs: Long = 30 * 60 * 1000
//    private var connectionExpiryTimeMillis: Long? = null
//    private var isRestoringConnection = false
    private var currentUserID: String? = null
    private var currentUserFullName: String? = null
    private var isAlertSent = false

    private val resetHandler = Handler(Looper.getMainLooper())
    private lateinit var resetRunnable: Runnable
    private var countDownTimer: CountDownTimer? = null

    private val animationStages = listOf(
        1 to 200,
        2 to 220,
        3 to 240,
        4 to 260,
        5 to 286,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize Views (Must use 'view.findViewById')
        connectionStatusTextView = view.findViewById(R.id.connection_status_textview)
        assistanceButton = view.findViewById(R.id.assistance_button)
        assistanceButtonBackground = view.findViewById(R.id.assistance_button_background)
        expirySection = view.findViewById(R.id.expiry_section)
        expiryDateText = view.findViewById(R.id.expiry_date_text)
        expiryTitle = view.findViewById(R.id.expiry_title)
        emergencyBanner = view.findViewById(R.id.emergency_contact_banner)

        // 2. Data Initialization
        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserID = sharedPreferences.getString("userID", null)
        currentUserFullName = sharedPreferences.getString("fullName", "User")

        resetRunnable = Runnable { forceResetButtonState() }

        // 3. UI Setup
        setupHoldToActivateButton()
        initializeUserName(view)

        // 4. Async DB Operations
//        val userId = currentUserID
//        if (userId != null) {
//            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
//                MySQLHelper.awaitInitialized()
//                val connectionInfo = MySQLHelper.getActiveConnectionForUser(userId)
//
//                withContext(Dispatchers.Main) {
//                    if (connectionInfo != null) {
//                        restoreConnectionState(connectionInfo.deviceID, connectionInfo.expiryTime)
//                    } else {
//                        updateConnectionStatusUI()
//                    }
//                }
//                MySQLHelper.cleanupExpiredConnections()
//            }
//        }
        registerFCMToken()
    }

    override fun onResume() {
        super.onResume()
//        checkUserAccessStatus()
//        restoreConnectionFromDatabase()
        refreshStateManually()
    }

    fun refreshStateManually() {
        if (!isAdded) return
        restoreConnectionFromDatabase()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        connectionTimer?.cancel()
        countDownTimer?.cancel()
        resetHandler.removeCallbacks(resetRunnable)
    }

    // --- LOGIC FUNCTIONS ---

    private fun checkUserAccessStatus() {
        val uid = currentUserID ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = try { MySQLHelper.getPwdProfileData(uid) } catch (e: Exception) { null }

            withContext(Dispatchers.Main) {
                if (profile != null) {
                    var accessIsExpired = false
                    if (profile.disabilityType == "Temporary") {
                        expirySection.visibility = View.VISIBLE
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val expiryDate = sdf.parse(profile.expiryDate ?: "")
                            if (expiryDate != null && expiryDate.before(Date())) {
                                accessIsExpired = true
                                expiryTitle.text = "Temporary Verification Expired"
                                expiryDateText.text = "Please visit the CHSW (Clinic) to reactivate."
                                if (connectedDeviceID != null) disconnectFromDevice(false)
                            } else {
                                expiryTitle.text = "Access Expiry"
                                expiryDateText.text = "Your temporary access expires on: ${profile.expiryDate}"
                            }
                        } catch (e: Exception) { accessIsExpired = false }
                    } else {
                        expirySection.visibility = View.GONE
                    }

                    // Button is only enabled if: Connected AND Not Expired
                    if (connectedDeviceID != null && !accessIsExpired) {
                        enableSOSButton()
                    } else {
                        // Determine why it is disabled for the Toast message
                        val reason = when {
                            accessIsExpired -> "Account Access Expired"
                            connectedDeviceID == null -> "Please connect to a wheelchair first"
                            else -> "System Unavailable"
                        }
                        disableSOSButton(reason)
                    }

                    if (!accessIsExpired && connectedDeviceID != null) enableSOSButton()
                    else disableSOSButton(if (accessIsExpired) "Verification Expired" else "Connect to Wheelchair")

                    emergencyBanner.visibility = if (profile.emergencyContactPerson.isNullOrBlank()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun enableSOSButton() {
        assistanceButton.isEnabled = true

        assistanceButton.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EE2D4C"))
        assistanceButtonBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E9C7CD"))

        assistanceButtonBackground.text = ""

        if (!isAlertSent) {
            assistanceButton.text = "SOS"
            assistanceButton.setTextColor(Color.WHITE)
        }
    }

    private fun disableSOSButton(reason: String) {
        assistanceButton.isEnabled = false
        assistanceButton.text = "SOS"
        assistanceButton.setTextColor(Color.WHITE) // Force text to be visible

        // Use contrasting grays so you can see the two separate circles
        assistanceButton.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9E9E9E")) // Dark Gray
        assistanceButtonBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0")) // Light Gray ring

        assistanceButtonBackground.text = "CONNECT TO A WHEELCHAIR"
        assistanceButtonBackground.setTextColor(Color.WHITE)

        assistanceButton.text = "SOS"
        assistanceButton.setTextColor(Color.WHITE)

        // Keep ring enabled for the Toast
        assistanceButtonBackground.isEnabled = true
        assistanceButtonBackground.setOnClickListener {
            Toast.makeText(requireContext(), reason, Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreConnectionFromDatabase() {
        val uid = currentUserID ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val active = withContext(Dispatchers.IO) { MySQLHelper.getActiveConnectionForUser(uid) }

            withContext(Dispatchers.Main) {
                if (active != null) {
                    connectedDeviceID = active.deviceID
                    // Tell Activity to change Scan -> Disconnect
                    (activity as? LocomotorDisabilityActivity)?.updateScanTabUI(true)
                } else {
                    connectedDeviceID = null
                    (activity as? LocomotorDisabilityActivity)?.updateScanTabUI(false)
                }
                updateConnectionStatusUI()
                checkUserAccessStatus() // Now it will correctly see the connectedDeviceID
            }
        }
    }

//    private fun startConnectionTimer(durationMs: Long) {
//        connectionTimer?.cancel()
//        connectionTimer = object : CountDownTimer(durationMs, 1000) {
//            override fun onTick(ms: Long) {
//                val mins = (ms / 1000) / 60
//                val secs = (ms / 1000) % 60
//                if (isAdded) { // Ensure fragment is still attached
//                    connectionStatusTextView.text = "Connected to: $connectedDeviceID\nTime left: ${String.format("%02d:%02d", mins, secs)}"
//                }
//            }
//            override fun onFinish() {
//                if (isAdded) {
//                    Toast.makeText(requireContext(), "Time limit reached.", Toast.LENGTH_LONG).show()
//                    disconnectFromDevice(false)
//                }
//            }
//        }.start()
//    }

    private fun disconnectFromDevice(showToast: Boolean = true) {
        val devId = connectedDeviceID ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val success = MySQLHelper.updateDeviceConnectionStatus(devId, null, null)
            withContext(Dispatchers.Main) {
                if (success) {
                    connectedDeviceID = null
                    updateConnectionStatusUI()
                    checkUserAccessStatus()
                    if (showToast) Toast.makeText(requireContext(), "Disconnected.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateConnectionStatusUI() {
        if (!isAdded) return
        if (connectedDeviceID != null) {
            connectionStatusTextView.text = "Connected to Wheelchair: $connectedDeviceID"
            connectionStatusTextView.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            connectionStatusTextView.text = "Not connected to a wheelchair."
            connectionStatusTextView.setTextColor(Color.GRAY)
        }
        // Update Bottom Nav via Activity if needed
        //(activity as? LocomotorDisabledMainActivity)?.updateScanTabTitle(connectedDeviceID != null)
    }

    private fun setupHoldToActivateButton() {
        assistanceButton.setOnTouchListener { _, event ->
            if (isAlertSent || !assistanceButton.isEnabled) return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startHoldAnimation(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { cancelHoldAnimation(); true }
                else -> false
            }
        }
    }

    private fun startHoldAnimation() {
        countDownTimer = object : CountDownTimer(5100, 1000) {
            override fun onTick(ms: Long) {
                val sec = 5 - (ms / 1000)
                if (sec in 1..5) {
                    assistanceButton.text = sec.toString()
                    animateButtonSize(dpToPx(animationStages[sec.toInt() - 1].second))
                }
            }
            override fun onFinish() {
                isAlertSent = true
                sendEmergencyAlert()
                playSuccessAnimation()
                resetHandler.postDelayed(resetRunnable, 30 * 60 * 1000)
            }
        }.start()
    }

    private fun cancelHoldAnimation() {
        if (!isAlertSent) {
            countDownTimer?.cancel()
            assistanceButton.text = "SOS"
            animateButtonSize(dpToPx(174))
        }
    }

    private fun forceResetButtonState() {
        isAlertSent = false
        assistanceButton.text = "SOS"
        assistanceButton.scaleX = 1.0f
        assistanceButton.scaleY = 1.0f
        animateButtonSize(dpToPx(174))
        checkUserAccessStatus()
    }

    private fun animateButtonSize(newSizePx: Int) {
        val animator = ValueAnimator.ofInt(assistanceButton.width, newSizePx)
        animator.duration = 200
        animator.addUpdateListener {
            val v = it.animatedValue as Int
            assistanceButton.layoutParams.width = v
            assistanceButton.layoutParams.height = v
            assistanceButton.requestLayout()
        }
        animator.start()
    }

    private fun playSuccessAnimation() {
        assistanceButton.text = "Help is on the way"
        assistanceButton.textSize = 20f
        ValueAnimator.ofFloat(1.0f, 1.05f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val s = it.animatedValue as Float
                assistanceButton.scaleX = s
                assistanceButton.scaleY = s
            }
            start()
        }
        triggerSuccessVibration()
    }

    private fun sendEmergencyAlert() {
        val uid = currentUserID ?: return
        val name = currentUserFullName ?: "User"
        val dev = connectedDeviceID ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.insertAssistanceRequestFromDevice(requireContext(), uid, name, dev, 0.0, 0.0, "Unknown")
            }
            if (!success && isAdded) Toast.makeText(requireContext(), "Alert failed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerSuccessVibration() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun initializeUserName(view: View) {
        view.findViewById<TextView>(R.id.user_fullname).text = "${currentUserFullName}"
    }

    private fun showDurationSelectionDialog(deviceID: String) {
        val options = arrayOf("30 minutes", "1 hour", "2 hours")
        val values = longArrayOf(1800000, 3600000, 7200000)

        AlertDialog.Builder(requireContext())
            .setTitle("Select Duration")
            .setItems(options) { _, which -> connectToDevice(deviceID, values[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun connectToDevice(deviceID: String, duration: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.updateDeviceConnectionStatus(deviceID, currentUserID, "expiry_date_string_here")
            }
            if (success && isAdded) {
                connectedDeviceID = deviceID
//                startConnectionTimer(duration)
                updateConnectionStatusUI()
                checkUserAccessStatus()
                (activity as? LocomotorDisabilityActivity)?.updateScanTabUI(true)
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

//    private fun restoreConnectionState(deviceID: String, expiryTime: Long) {
//        connectedDeviceID = deviceID
//        val remainingTime = expiryTime - System.currentTimeMillis()
//        if (remainingTime > 0) startConnectionTimer(remainingTime)
//        else disconnectFromDevice(false)
//    }

    private fun registerFCMToken() { /* Keep existing logic but use requireContext() */ }

//    fun handleQrTabClick() {
//        if (connectedDeviceID == null) launchQrScanner()
//        else {
//            AlertDialog.Builder(requireContext())
//                .setTitle("Disconnect")
//                .setMessage("Disconnect from $connectedDeviceID?")
//                .setPositiveButton("Yes") { _, _ -> disconnectFromDevice() }
//                .setNegativeButton("No", null).show()
//        }
//    }

//    private fun launchQrScanner() {
//        qrCodeScannerLauncher.launch(ScanOptions().setPrompt("Scan Wheelchair QR Code"))
//    }
}