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
import android.widget.ProgressBar
import android.widget.SeekBar
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

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
    private lateinit var sosProgressBar: ProgressBar
    private lateinit var sosSlider: SeekBar
    private lateinit var sosInstructionText: TextView
    private lateinit var sosHeaderText: TextView
    private lateinit var sosSliderContainer: View
    private lateinit var sosSliderText: TextView
    private lateinit var monitoringCard: MaterialCardView

    private var pendingTimer: CountDownTimer? = null
    private var isSOSPending = false
    private var lastTickProgress = 0
    private var hasHandledSliderCancel = false

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
    // private var countDownTimer: CountDownTimer? = null

    // private val animationStages = listOf(1 to 200, 2 to 220, 3 to 240, 4 to 260, 5 to 286)

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
        sosProgressBar = view.findViewById(R.id.sos_circular_progress)
        sosSlider = view.findViewById(R.id.sos_slide_to_cancel)
        sosInstructionText = view.findViewById(R.id.instruction_text)
        sosHeaderText = view.findViewById(R.id.emergency_pending_text)
        sosSliderContainer = view.findViewById(R.id.sos_slider_container)
        sosSliderText = view.findViewById(R.id.sos_slider_text)
        monitoringCard = view.findViewById(R.id.available_officers_card)

        assistanceButtonBackground.isClickable = false
        assistanceButtonBackground.isFocusable = false

        assistanceButton.setOnClickListener {
            if (!isAlertSent && !isSOSPending && assistanceButton.isEnabled) {
                startSOSPendingFlow()
            } else if (isSOSPending) {
                // Logic: If they tap the button AGAIN while counting down, send it immediately
                finalizePendingSOSAndSend()
            }
        }

        sosSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 1. Fade out the text (as we did before)
                val alpha = 1 - (progress.toFloat() / 100)
                sosSliderText.alpha = alpha

                // 2. Subtle Tick Haptic: Trigger every 15% of movement
                if (Math.abs(progress - lastTickProgress) > 15) {
                    triggerHaptic("TICK")
                    lastTickProgress = progress
                }

                // 3. Success Haptic
                if (progress >= 95 && isSOSPending && !hasHandledSliderCancel) {
                    hasHandledSliderCancel = true
                    triggerHaptic("CONFIRM") // Distinct "thud" for success
                    resetUItoInitialState()
                    Toast.makeText(requireContext(), "SOS Cancelled", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                lastTickProgress = 0
                hasHandledSliderCancel = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (!hasHandledSliderCancel && sosSlider.progress < 95) {
                    sosSlider.progress = 0
                    sosSliderText.alpha = 1.0f
                    // Short vibration to show it snapped back/failed
                    triggerHaptic("TICK")
                }
            }
        })

        // 2. Data Initialization
        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserID = sharedPreferences.getString("userID", null)
        currentUserFullName = sharedPreferences.getString("fullName", "User")

        resetRunnable = Runnable { forceResetButtonState() }

        // 3. UI Setup
        view.findViewById<TextView>(R.id.user_fullname).text = "${currentUserFullName}!"

        // 4. Restore Database State (Critical Assistance Feature)
        restoreConnectionFromDatabase()
        startIncidentPolling()
    }

    override fun onResume() {
        super.onResume()
        refreshStateManually()
    }

    override fun onPause() {
        super.onPause()
        pendingTimer?.cancel() // Stop the SOS countdown if they exit the app
    }

    fun refreshStateManually() {
        if (!isAdded) return
        checkUserAccessStatus()
        restoreConnectionFromDatabase()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        connectionTimer?.cancel()
        pendingTimer?.cancel()
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

        if (!isAlertSent && !isSOSPending) {
            assistanceButton.text = "SOS"
            assistanceButton.textSize = 50f
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

        // Ensure the background is not interactive
        assistanceButtonBackground.isClickable = false
        assistanceButtonBackground.isFocusable = false

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

    private fun startSOSPendingFlow() {
        isSOSPending = true

        val targetSizePx = dpToPx(286)

        val layoutParams = assistanceButton.layoutParams
        layoutParams.width = targetSizePx
        layoutParams.height = targetSizePx
        assistanceButton.layoutParams = layoutParams

        // UI Changes
        sosHeaderText.text = "SOS pending..."
        sosInstructionText.text = "Tap the button to send SOS now\nor slide to cancel"
        sosProgressBar.visibility = View.VISIBLE
        sosSliderContainer.visibility = View.VISIBLE // Show the whole container
        sosSliderText.alpha = 1.0f
        sosSlider.visibility = View.VISIBLE
        sosSlider.progress = 0

        // 5-Second Timer
        pendingTimer = object : CountDownTimer(5000, 50) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                assistanceButton.text = secondsLeft.toString()

                // Update circular progress (0 to 100)
                val progress = ((5000 - millisUntilFinished).toFloat() / 5000 * 100).toInt()
                sosProgressBar.progress = progress
            }

            override fun onFinish() {
                finalizePendingSOSAndSend()
            }
        }.start()
    }

    private fun finalizePendingSOSAndSend() {
        if (!isSOSPending || isAlertSent) return

        pendingTimer?.cancel()
        pendingTimer = null

        isSOSPending = false
        isAlertSent = true
        sosProgressBar.visibility = View.GONE
        sosSliderContainer.visibility = View.GONE
        sosSlider.visibility = View.GONE
        sosInstructionText.text = "Wait for an officer to respond"
        assistanceButton.text = "" // Will be set by playSuccessAnimation

        sendEmergencyAlert() // Actual DB call
        playSuccessAnimation()
    }

    private fun forceResetButtonState() {
        pendingTimer?.cancel()
        isSOSPending = false
        isAlertSent = false

        sosProgressBar.visibility = View.GONE
        sosSliderContainer.visibility = View.GONE
        sosSlider.visibility = View.GONE
        sosHeaderText.text = "Emergency help needed?"

        assistanceButton.text = "SOS"
        assistanceButton.textSize = 50f
        assistanceButton.scaleX = 1.0f
        assistanceButton.scaleY = 1.0f
        assistanceButton.clearAnimation()

        monitoringCard.visibility = View.VISIBLE
        checkUserAccessStatus()
    }

    private fun resetUItoInitialState() {
        triggerHaptic("CONFIRM")

        // 1. Stop the countdown timer immediately
        pendingTimer?.cancel()
        isSOSPending = false
        isAlertSent = false

        // 2. Reset Button Size (Shrink back to 174dp)
        val originalSizePx = dpToPx(174)
        val params = assistanceButton.layoutParams
        params.width = originalSizePx
        params.height = originalSizePx
        assistanceButton.layoutParams = params

        // Ensure any animations (like pulsating) are stopped
        assistanceButton.clearAnimation()
        assistanceButton.scaleX = 1.0f
        assistanceButton.scaleY = 1.0f

        // 3. Reset Text and Styles
        assistanceButton.text = "SOS"
        assistanceButton.textSize = 50f
        sosHeaderText.text = "Emergency help needed?"
        sosInstructionText.text = "Press the button below to alert a safety officer in the campus"

        // 4. Reset Visibility
        sosProgressBar.visibility = View.GONE
        sosProgressBar.progress = 0
        sosSliderContainer.visibility = View.GONE
        sosSlider.visibility = View.GONE
        sosSlider.progress = 0
        sosSliderText.alpha = 1.0f

        monitoringCard.visibility = View.VISIBLE

        // 5. Important: Re-run the access check to set correct colors (Red vs Gray)
        checkUserAccessStatus()
    }

    private fun playSuccessAnimation() {
        assistanceButton.text = "ALERT SENT"
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
            val phoneLocation = getCurrentPhoneLocation()
            val lat = phoneLocation?.latitude ?: 0.0
            val lng = phoneLocation?.longitude ?: 0.0
            val floor = "Live Phone GPS"

            if (phoneLocation == null && isAdded) {
                Toast.makeText(requireContext(), "Live phone location unavailable. Sending SOS with fallback coordinates.", Toast.LENGTH_SHORT).show()
            }

            if (phoneLocation != null) {
                withContext(Dispatchers.IO) {
                    MySQLHelper.upsertLiveGPS(userID, phoneLocation.latitude, phoneLocation.longitude, phoneLocation.accuracy)
                }
            }

            Log.d("Assistance", "Sending Alert for $fullName using phone GPS. Location found: ${phoneLocation != null}")

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
                withContext(Dispatchers.IO) {
                    MySQLHelper.upsertLiveGPS(userID, lat, lng, phoneLocation?.accuracy ?: 0f)
                }
                startGpsUpdates()
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

    private fun triggerHaptic(type: String) {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (type) {
                "CONFIRM" -> {
                    // A strong, distinct double pulse for success
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150), intArrayOf(0, 255, 0, 255), -1)
                    vibrator.vibrate(effect)
                }
                "TICK" -> {
                    // A very light, short tick for sliding
                    vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } else {
            // Fallback for older devices
            @Suppress("DEPRECATION")
            vibrator.vibrate(if (type == "CONFIRM") 300 else 15)
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

                // If SOS button is stuck on "ALERT SENT", check if incident is fully resolved
                if (isAlertSent) {
                    val stillActive = withContext(Dispatchers.IO) {
                        MySQLHelper.hasActiveIncidentForUser(uid)
                    }
                    if (!stillActive) {
                        stopGpsUpdates()
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

    private data class PhoneLocation(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float
    )

    private suspend fun getCurrentPhoneLocation(): PhoneLocation? {
        val ctx = context ?: return null
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return null
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(ctx)

        val current = suspendCancellableCoroutine<android.location.Location?> { continuation ->
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
                .addOnCanceledListener { continuation.resume(null) }
        }

        if (current != null) {
            return PhoneLocation(
                latitude = current.latitude,
                longitude = current.longitude,
                accuracy = current.accuracy
            )
        }

        val lastKnown = suspendCancellableCoroutine<android.location.Location?> { continuation ->
            fusedClient.lastLocation
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
                .addOnCanceledListener { continuation.resume(null) }
        }

        return lastKnown?.let {
            PhoneLocation(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracy = it.accuracy
            )
        }
    }

}