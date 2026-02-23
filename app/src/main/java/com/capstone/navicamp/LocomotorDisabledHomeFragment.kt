package com.capstone.navicamp

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.firebase.messaging.FirebaseMessaging
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.material.card.MaterialCardView
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
    private lateinit var officerDispatchCard: MaterialCardView
    private lateinit var officerDispatchName: TextView
    private lateinit var availableOfficersText: TextView

    // State Variables
    private var connectedDeviceID: String? = null
    private var connectionTimer: CountDownTimer? = null
    private var currentUserID: String? = null
    private var currentUserFullName: String? = null
    private var isAlertSent = false
    private var isRestoringConnection = false

    // Polling & GPS
    private var currentIncident: OngoingIncidentInfo? = null
    private val incidentPollHandler = Handler(Looper.getMainLooper())
    private var isPollingIncident = false
    private var locationCallback: LocationCallback? = null

    private val resetHandler = Handler(Looper.getMainLooper())
    private lateinit var resetRunnable: Runnable
    private var countDownTimer: CountDownTimer? = null

    private val animationStages = listOf(1 to 200, 2 to 220, 3 to 240, 4 to 260, 5 to 286)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize Views
        connectionStatusTextView = view.findViewById(R.id.connection_status_textview)
        assistanceButton = view.findViewById(R.id.assistance_button)
        assistanceButtonBackground = view.findViewById(R.id.assistance_button_background)
        expirySection = view.findViewById(R.id.expiry_section)
        expiryDateText = view.findViewById(R.id.expiry_date_text)
        expiryTitle = view.findViewById(R.id.expiry_title)
        emergencyBanner = view.findViewById(R.id.emergency_contact_banner)
        officerDispatchCard = view.findViewById(R.id.officer_dispatch_card)
        officerDispatchName = view.findViewById(R.id.officer_dispatch_name)
        availableOfficersText = view.findViewById(R.id.available_officers_count_text)

        // 2. Data Initialization
        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserID = sharedPreferences.getString("userID", null)
        currentUserFullName = sharedPreferences.getString("fullName", "User")

        resetRunnable = Runnable { forceResetButtonState() }

        // 3. UI Setup
        setupHoldToActivateButton()
        view.findViewById<TextView>(R.id.user_fullname).text = "${currentUserFullName}!"

        // 4. Restore Database State (Critical Assistance Feature)
        restoreConnectionFromDatabase()
        startIncidentPolling()
    }

    override fun onResume() {
        super.onResume()
        refreshStateManually()
    }

    fun refreshStateManually() {
        if (!isAdded) return
        checkUserAccessStatus()
        restoreConnectionFromDatabase()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        connectionTimer?.cancel()
        countDownTimer?.cancel()
        resetHandler.removeCallbacks(resetRunnable)
        stopIncidentPolling()
        stopGpsUpdates()
    }

    private fun checkUserAccessStatus() {
        val uid = currentUserID ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
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
                                expiryDateText.text = "Please visit the CHSW (Clinic) to reactivate features."
                                if (connectedDeviceID != null) disconnectFromDevice(false)
                            } else {
                                expiryTitle.text = "Access Expiry"
                                expiryDateText.text = "Your temporary access expires on: ${profile.expiryDate}"
                            }
                        } catch (e: Exception) { accessIsExpired = false }
                    } else {
                        expirySection.visibility = View.GONE
                    }

                    // Consolidated Button State
                    if (connectedDeviceID != null && !accessIsExpired) {
                        enableSOSButton()
                    } else {
                        val reason = if (accessIsExpired) "Account Expired" else "Please Connect to a Wheelchair"
                        disableSOSButton(reason)
                    }

                    emergencyBanner.visibility = if (profile.emergencyContactPerson.isNullOrBlank()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun enableSOSButton() {
        assistanceButton.isEnabled = true
        assistanceButton.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EE2D4C"))
        assistanceButtonBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E9C7CD"))
        assistanceButtonBackground.text = "" // Hide "Connect to Wheelchair" text
        if (!isAlertSent) {
            assistanceButton.text = "SOS"
            assistanceButton.setTextColor(Color.WHITE)
        }
    }

    private fun disableSOSButton(reason: String) {
        assistanceButton.isEnabled = false

        // Use high-contrast grays so inner button is visible
        assistanceButton.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#616161")) // Deep Gray
        assistanceButtonBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F5F5")) // Very Light Gray

        // Force inner button to stay on top
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            assistanceButton.translationZ = 10f
        }

        assistanceButtonBackground.text = "CONNECT TO A WHEELCHAIR"
        assistanceButtonBackground.setTextColor(Color.parseColor("#757575"))
        assistanceButton.text = "SOS"
        assistanceButton.setTextColor(Color.WHITE)
    }

    private fun restoreConnectionFromDatabase() {
        if (currentUserID == null || isRestoringConnection) return
        isRestoringConnection = true

        viewLifecycleOwner.lifecycleScope.launch {
            val active = withContext(Dispatchers.IO) { MySQLHelper.getActiveConnectionForUser(currentUserID!!) }
            withContext(Dispatchers.Main) {
                if (active != null) {
                    connectedDeviceID = active.deviceID
                    (activity as? LocomotorDisabilityActivity)?.updateScanTabUI(true)
                } else {
                    connectedDeviceID = null
                    (activity as? LocomotorDisabilityActivity)?.updateScanTabUI(false)
                }
                updateConnectionStatusUI()
                checkUserAccessStatus() // Refresh SOS button color
                isRestoringConnection = false
            }
        }
    }

    private fun disconnectFromDevice(showToast: Boolean = true) {
        val devId = connectedDeviceID ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val success = MySQLHelper.updateDeviceConnectionStatus(devId, null, null)
            withContext(Dispatchers.Main) {
                if (success) {
                    connectedDeviceID = null
                    updateConnectionStatusUI()
                    checkUserAccessStatus()
                    (activity as? LocomotorDisabilityActivity)?.updateScanTabUI(false)
                    if (showToast) Toast.makeText(requireContext(), "Disconnected.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateConnectionStatusUI() {
        if (!isAdded) return
        if (connectedDeviceID != null) {
            connectionStatusTextView.text = "Connected to: $connectedDeviceID"
            connectionStatusTextView.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            connectionStatusTextView.text = "Not connected to a wheelchair"
            connectionStatusTextView.setTextColor(Color.GRAY)
        }
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
        assistanceButton.textSize = 50f
        assistanceButton.scaleX = 1.0f
        assistanceButton.scaleY = 1.0f
        assistanceButton.clearAnimation()
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

    private fun requestAssistanceWithDevice(deviceID: String, userID: String, fullName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Fetch the Wheelchair's actual location from the DB (restored from past code)
            val deviceLocation: MySQLHelper.DeviceLocation? = withContext(Dispatchers.IO) {
                MySQLHelper.getDeviceLastLocation(deviceID)
            }

            // FALLBACK LOGIC: If no location is found, use 0.0 so the SOS still goes through
            val lat = deviceLocation?.latitude ?: 0.0
            val lng = deviceLocation?.longitude ?: 0.0
            val floor = deviceLocation?.floorLevel ?: "Location Unknown"

            Log.d("Assistance", "Sending Alert for $fullName. Device location found: ${deviceLocation != null}")

            // 2. Perform the actual Database Insertion
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.insertAssistanceRequestFromDevice(
                    requireContext(),
                    userID,
                    fullName,
                    deviceID,
                    lat,
                    lng,
                    floor
                )
            }

            if (success) {
                triggerSuccessVibration()
                Log.d("Assistance", "SOS successful. Haptic feedback triggered.")
            } else {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Server Error: Could not send alert.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendEmergencyAlert() {
        val uid = currentUserID
        val name = currentUserFullName
        val devId = connectedDeviceID

        if (uid != null && name != null) {
            if (devId != null) {
                // This triggers the heavy lifting function above
                requestAssistanceWithDevice(devId, uid, name)
            } else {
                // Case: User somehow triggered SOS without being connected to a wheelchair
                if (isAdded) {
                    Toast.makeText(requireContext(), "Alert sent using phone location (Manual SOS)", Toast.LENGTH_LONG).show()
                }
                Log.d("Assistance", "Manual SOS triggered without wheelchair connection.")
            }
        } else {
            if (isAdded) {
                Toast.makeText(requireContext(), "Error: User details missing. Please log in again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun triggerSuccessVibration() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.EFFECT_HEAVY_CLICK))
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // --- Polling & Dispatch Logic ---

    private fun refreshAvailableOfficersCount() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val count = MySQLHelper.getAvailableOfficersCount()

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    availableOfficersText.text = count.toString()
                }
            }
        }
    }

    private val incidentPollRunnable = object : Runnable {
        override fun run() {
            if (!isPollingIncident || !isAdded) return
            pollOngoingIncident()
            refreshAvailableOfficersCount() // ADD THIS LINE
            incidentPollHandler.postDelayed(this, 5000)
        }
    }

    private fun startIncidentPolling() {
        isPollingIncident = true
        incidentPollHandler.post(incidentPollRunnable)
    }

    private fun stopIncidentPolling() {
        isPollingIncident = false
        incidentPollHandler.removeCallbacks(incidentPollRunnable)
    }

    private fun pollOngoingIncident() {
        val uid = currentUserID ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val incident = withContext(Dispatchers.IO) { MySQLHelper.getOngoingIncidentForUser(uid) }
            if (!isAdded) return@launch

            if (incident != null) {
                if (currentIncident == null) startGpsUpdates()
                currentIncident = incident
                officerDispatchName.text = "${incident.officerName} is dispatched"
                officerDispatchCard.visibility = View.VISIBLE
                officerDispatchCard.setOnClickListener { openOfficerTrackingMap(incident) }
            } else {
                // No ongoing incident with an officer — clean up dispatch card
                if (currentIncident != null) {
                    val oldOfficerID = currentIncident?.officerUserID
                    stopGpsUpdates()
                    if (oldOfficerID != null) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            MySQLHelper.deleteLiveGPS(oldOfficerID)
                        }
                    }
                }
                currentIncident = null
                officerDispatchCard.visibility = View.GONE

                // If SOS button is stuck on "Help is on the way", check if incident is fully resolved
                if (isAlertSent) {
                    val stillActive = withContext(Dispatchers.IO) {
                        MySQLHelper.hasActiveIncidentForUser(uid)
                    }
                    if (!stillActive) {
                        resetHandler.removeCallbacks(resetRunnable)
                        forceResetButtonState()
                    }
                }
            }
        }
    }

    private fun openOfficerTrackingMap(incident: OngoingIncidentInfo) {
        val intent = Intent(requireContext(), OfficerTrackingMapActivity::class.java).apply {
            putExtra("OFFICER_NAME", incident.officerName)
            putExtra("OFFICER_USER_ID", incident.officerUserID)
            putExtra("MY_USER_ID", currentUserID)
        }
        startActivity(intent)
    }

    private fun startGpsUpdates() {
        if (locationCallback != null) return // Already running
        val ctx = context ?: return

        // 1. Check for Permissions
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Request permission if not granted
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(ctx)

        // 2. Create Location Request (High accuracy, updates every 5 seconds)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(1.0f) // Only update if they move 1 meter
            .build()

        // 3. Define what happens when a new location is found
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val uid = currentUserID ?: return

                // Send the phone's live coordinates to the DB for the Officer to see
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    MySQLHelper.upsertLiveGPS(uid, loc.latitude, loc.longitude, loc.accuracy)
                }
            }
        }

        // 4. Start the updates
        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun stopGpsUpdates() {
        val ctx = context ?: return

        // 1. Stop the phone's hardware from tracking GPS (Saves battery)
        locationCallback?.let {
            LocationServices.getFusedLocationProviderClient(ctx).removeLocationUpdates(it)
        }
        locationCallback = null

        // 2. Clean up your entry in the Database (Saves privacy)
        val uid = currentUserID ?: return
        // Use a separate scope here to ensure the delete happens even if fragment is closing
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            MySQLHelper.deleteLiveGPS(uid)
        }
    }

}