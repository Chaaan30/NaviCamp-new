package com.capstone.navicamp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.material.card.MaterialCardView
import com.capstone.navicamp.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class OfficerHomeFragment : Fragment(R.layout.fragment_home_safetyofficer) {

    private val viewModel: SecurityOfficerViewModel by viewModels()
    private lateinit var assistanceLayout: LinearLayout
    private lateinit var assistanceSectionTitle: TextView
    private lateinit var smartPollingManager: SmartPollingManager
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var onDutySwitch: SwitchCompat
    private lateinit var onDutyLabel: TextView
    private lateinit var onDutyAutoLabel: TextView
    private var suppressOnDutySwitchCallback = false
    private var officerFullName: String = "Officer"
    private var presenceListener: ListenerRegistration? = null
    private var onlineUsersIndicator: TextView? = null

    // Geolocation-based on-duty detection
    private var geofenceLocationCallback: LocationCallback? = null
    private var lastOnDutyState: Boolean? = null // Track to avoid redundant DB updates
    private var officerUserID: String = ""

    companion object {
        private const val TAG = "OfficerHomeFragment"

        // School center coordinates (Mapúa Malayan Colleges Laguna)
        private const val SCHOOL_CENTER_LAT = 14.24422110217503
        private const val SCHOOL_CENTER_LNG = 121.112341209786
        private const val GEOFENCE_RADIUS_METERS = 220f // 220m radius

        private const val LOCATION_UPDATE_INTERVAL_MS = 10_000L // 10 seconds
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize Views
        val secoffFullname = view.findViewById<TextView>(R.id.secoff_fullname)
        assistanceSectionTitle = view.findViewById<TextView>(R.id.assistance_section_title)
        assistanceLayout = view.findViewById<LinearLayout>(R.id.assistance_layout)
        val registeredUsersCard = view.findViewById<MaterialCardView>(R.id.registered_users_card)
        val iotDevicesCard = view.findViewById<MaterialCardView>(R.id.iot_devices_card)
        val registeredUsersText = view.findViewById<TextView>(R.id.registered_users)
        val iotDevicesText = view.findViewById<TextView>(R.id.iot_devices)
        onlineUsersIndicator = view.findViewById(R.id.online_users_indicator)
        emptyStateLayout = view.findViewById(R.id.empty_state_layout)
        onDutySwitch = view.findViewById(R.id.on_duty_switch)
        onDutyLabel = view.findViewById(R.id.on_duty_label)
        onDutyAutoLabel = view.findViewById(R.id.on_duty_auto_label)

        // Disable manual toggle — on-duty is now controlled by geolocation
        onDutySwitch.isEnabled = false
        onDutySwitch.isClickable = false

        // 2. Setup Click Listeners

        registeredUsersCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.officer_fragment_container, RegisteredUsersFragment())
                .addToBackStack(null) // Allows user to press "Back" to return home
                .commit()
        }

        iotDevicesCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.officer_fragment_container, WheelchairManagementFragment())
                .addToBackStack(null)
                .commit()
        }

        // 3. Setup Smart Polling
        smartPollingManager = SmartPollingManager.getInstance()
        setupSmartPollingListeners()

        // 4. Setup Observers
        setupObservers(view)

        // 5. Initial Data Fetch
        initializeUserName(view)
        initializeGeolocationOnDuty()
        viewModel.fetchPendingItems(officerFullName)
        viewModel.fetchUserCount()
        viewModel.fetchDeviceCount()
    }

    private fun startPresenceListener() {
        val firestore = FirebaseFirestore.getInstance()
        val twoMinutesAgo = com.google.firebase.Timestamp(
            Date(System.currentTimeMillis() - 2 * 60 * 1000)
        )

        presenceListener = firestore.collection("presence")
            .whereEqualTo("user_type", "locomotor")
            .whereGreaterThan("last_seen", twoMinutesAgo)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val onlineCount = snapshot.size()
                onlineUsersIndicator?.text = "\u25cf $onlineCount online now"
            }
    }

    private fun stopPresenceListener() {
        presenceListener?.remove()
        presenceListener = null
    }

    private fun setupObservers(view: View) {
        val registeredUsersText = view.findViewById<TextView>(R.id.registered_users)
        val iotDevicesText = view.findViewById<TextView>(R.id.iot_devices)

        viewModel.pendingItems.observe(viewLifecycleOwner) { pendingItems ->
            updateAssistanceCards(pendingItems)
        }

        viewModel.userCount.observe(viewLifecycleOwner) { count ->
            registeredUsersText.text = count.toString()
        }

        viewModel.deviceCount.observe(viewLifecycleOwner) { count ->
            iotDevicesText.text = count.toString()
        }
    }

    private fun initializeUserName(view: View) {
        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", "Officer")
        officerFullName = fullName ?: "Officer"
        // Fixed: ID was changed to home_secoff_fullname in XML
        view.findViewById<TextView>(R.id.secoff_fullname)?.text = fullName
    }

    /**
     * Initializes geolocation-based on-duty detection.
     * Replaces the old manual switch logic.
     * Continuously monitors the officer's GPS and automatically
     * toggles on-duty status based on proximity to the school campus.
     */
    private fun initializeGeolocationOnDuty() {
        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
        officerUserID = sharedPreferences.getString("userID", null).orEmpty()
        if (officerUserID.isBlank()) {
            onDutySwitch.isChecked = false
            updateOnDutyUI(false)
            return
        }

        // First, load the current on-duty status from the DB
        viewLifecycleOwner.lifecycleScope.launch {
            val isOnDuty = withContext(Dispatchers.IO) {
                MySQLHelper.getSafetyOfficerOnDutyStatus(officerUserID)
            }
            suppressOnDutySwitchCallback = true
            onDutySwitch.isChecked = isOnDuty
            suppressOnDutySwitchCallback = false
            lastOnDutyState = isOnDuty
            updateOnDutyUI(isOnDuty)
        }

        // Start geolocation monitoring
        startGeofenceMonitoring()
    }

    /**
     * Starts continuous location monitoring to detect if the officer
     * is within the school geofence radius.
     */
    private fun startGeofenceMonitoring() {
        if (geofenceLocationCallback != null) return // Already monitoring
        val ctx = context ?: return

        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 3001)
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(ctx)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(5f) // Only update if moved 5 meters
            .build()

        geofenceLocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                checkGeofenceStatus(loc)
            }
        }

        fusedClient.requestLocationUpdates(request, geofenceLocationCallback!!, Looper.getMainLooper())
        Log.d(TAG, "Started geofence monitoring for officer: $officerUserID")

        // Also do an immediate check with last known location
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                checkGeofenceStatus(loc)
            }
        }
    }

    private fun stopGeofenceMonitoring() {
        val ctx = context ?: return
        geofenceLocationCallback?.let {
            LocationServices.getFusedLocationProviderClient(ctx).removeLocationUpdates(it)
        }
        geofenceLocationCallback = null
        Log.d(TAG, "Stopped geofence monitoring")
    }

    /**
     * Checks if the officer's current location is within the school geofence.
     * Updates on-duty status in DB and UI accordingly.
     */
    private fun checkGeofenceStatus(currentLocation: Location) {
        val distance = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            SCHOOL_CENTER_LAT, SCHOOL_CENTER_LNG,
            distance
        )

        val isWithinGeofence = distance[0] <= GEOFENCE_RADIUS_METERS
        val distanceMeters = distance[0].toInt()

        Log.d(TAG, "Geofence check: distance=${distanceMeters}m, within=$isWithinGeofence, radius=${GEOFENCE_RADIUS_METERS.toInt()}m")

        // Only update if the state actually changed
        if (lastOnDutyState == isWithinGeofence) return
        lastOnDutyState = isWithinGeofence

        if (officerUserID.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                MySQLHelper.updateSafetyOfficerOnDutyStatus(officerUserID, isWithinGeofence)
            }

            if (!isAdded) return@launch

            if (updated) {
                suppressOnDutySwitchCallback = true
                onDutySwitch.isChecked = isWithinGeofence
                suppressOnDutySwitchCallback = false
                updateOnDutyUI(isWithinGeofence)

                if (!isWithinGeofence) {
                    Toast.makeText(
                        requireContext(),
                        "You are not within the school area. You are currently off-duty and unable to respond to assistance requests from locomotor disabled users.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "You are within the school area. You are now on-duty.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.e(TAG, "Failed to update on-duty status in database")
            }
        }
    }

    /**
     * Updates the on-duty badge and auto-label text based on the current state.
     */
    private fun updateOnDutyUI(isOnDuty: Boolean) {
        if (!isAdded) return
        if (isOnDuty) {
            onDutyLabel.text = "ON DUTY"
            onDutyLabel.setBackgroundResource(R.drawable.badge_on_duty)
            onDutyAutoLabel.text = "📍 Within school area"
            onDutyAutoLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
        } else {
            onDutyLabel.text = "OFF DUTY"
            onDutyLabel.setBackgroundResource(R.drawable.badge_off_duty)
            onDutyAutoLabel.text = "📍 Outside school area"
            onDutyAutoLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 3001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGeofenceMonitoring()
        }
    }

    // This moves the logic from your old Activity to the Fragment
    private fun updateAssistanceCards(pendingItems: List<LocationItem>) {
        val sortedItems = pendingItems.sortedWith(
            compareBy<LocationItem> { statusPriority(it.status) }
                .thenByDescending { it.dateTime }
        )

        assistanceSectionTitle.text = "People currently in need of assistance (${sortedItems.size}):"
        assistanceLayout.removeAllViews()

        if (sortedItems.isEmpty()) {
            // empty state
            emptyStateLayout.visibility = View.VISIBLE
            assistanceLayout.visibility = View.GONE
            return
        } else {
        // active list
        emptyStateLayout.visibility = View.GONE
        assistanceLayout.visibility = View.VISIBLE
    }

        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        for (item in sortedItems) {
            val cardView = LayoutInflater.from(requireContext())
                .inflate(R.layout.assistance_card, assistanceLayout, false)

            setupAssistanceCard(cardView, item, inputFormat, dateFormat, timeFormat)
            assistanceLayout.addView(cardView)
        }
    }

    private fun statusPriority(status: String?): Int {
        return when (status?.trim()?.lowercase()) {
            "ongoing" -> 0
            "pending" -> 1
            "resolved" -> 2
            else -> 3
        }
    }

    private fun setupAssistanceCard(
        cardView: View,
        item: LocationItem,
        inputFormat: SimpleDateFormat,
        dateFormat: SimpleDateFormat,
        timeFormat: SimpleDateFormat
    ) {
        val fullNameTextView = cardView.findViewById<TextView>(R.id.full_name_text)
        val createdOnDateTextView = cardView.findViewById<TextView>(R.id.created_on_date_text)
        val createdOnTimeTextView = cardView.findViewById<TextView>(R.id.created_on_time_text)
        val floorLevelTextView = cardView.findViewById<TextView>(R.id.floor_level_text)
        val officerRespondedTextView = cardView.findViewById<TextView>(R.id.officer_responded_text)
        val assistanceStatusTextView = cardView.findViewById<TextView>(R.id.assistance_status_text)
        val respondButton = cardView.findViewById<Button>(R.id.respond_button)
        val assistanceTypeBadge = cardView.findViewById<TextView>(R.id.assistance_type_badge)

        fullNameTextView.text = item.fullName
        try {
            val date = inputFormat.parse(item.dateTime)
            createdOnDateTextView.text = date?.let { dateFormat.format(it) } ?: item.dateTime
            createdOnTimeTextView.text = date?.let { timeFormat.format(it) } ?: item.dateTime
        } catch (e: Exception) {
            createdOnDateTextView.text = item.dateTime
            createdOnTimeTextView.text = ""
        }

        // 4. Set the Floor Level
        floorLevelTextView.text = item.floorLevel

        // 5. Set the Officer Status
        val officerName = item.officerName
        if (!officerName.isNullOrEmpty()) {
            officerRespondedTextView.text = "Officer: $officerName"
        } else {
            officerRespondedTextView.text = "No officer responded yet"
        }

        // Assistance type badge logic
        if (item.assistanceType == "FALL_DETECTION") {
            assistanceTypeBadge.text = "FALL ALERT"
            assistanceTypeBadge.setBackgroundResource(R.drawable.badge_fall_detection)
        } else {
            assistanceTypeBadge.text = "MANUAL"
            assistanceTypeBadge.setBackgroundResource(R.drawable.badge_manual_assistance)
        }

        // Resolved requests without report should route to report flow.
        val isResolved = item.status.equals("resolved", ignoreCase = true)
        respondButton.text = if (isResolved) "Make a Report" else "Respond"

        val normalizedStatus = item.status.trim().lowercase()
        when (normalizedStatus) {
            "ongoing" -> {
                assistanceStatusTextView.text = "Status: ONGOING"
                assistanceStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue))
            }
            "resolved" -> {
                assistanceStatusTextView.text = "Status: RESOLVED (REPORT PENDING)"
                assistanceStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
            }
            else -> {
                assistanceStatusTextView.text = "Status: PENDING"
                assistanceStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
            }
        }

        respondButton.setOnClickListener {
            val mapFragment = MapHomeFragment.newInstance(
                locationID = item.locationID,
                latitude = item.latitude,
                longitude = item.longitude,
                fullName = item.fullName,
                floorLevel = item.floorLevel,
                status = item.status
            )
            (activity as? SecurityOfficerActivity)?.navigateToMapHome(mapFragment)
        }
    }

    private fun setupSmartPollingListeners() {
        smartPollingManager.onDataUpdate = {
            viewModel.fetchPendingItems(officerFullName)
            viewModel.fetchUserCount()
            viewModel.fetchDeviceCount()
        }
    }

    override fun onResume() {
        super.onResume()
        smartPollingManager.startPolling()
        startPresenceListener()
        // Restart geofence monitoring when fragment resumes
        if (officerUserID.isNotBlank()) {
            startGeofenceMonitoring()
        }
    }

    override fun onPause() {
        super.onPause()
        smartPollingManager.stopPolling()
        stopPresenceListener()
        stopGeofenceMonitoring()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopGeofenceMonitoring()
    }
}