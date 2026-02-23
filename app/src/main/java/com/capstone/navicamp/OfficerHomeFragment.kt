package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.capstone.navicamp.R
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
        emptyStateLayout = view.findViewById(R.id.empty_state_layout)

        // 2. Setup Click Listeners

        registeredUsersCard.setOnClickListener {
            startActivity(Intent(requireContext(), DisplayRegisteredUsersActivity::class.java))
        }

        iotDevicesCard.setOnClickListener {
            startActivity(Intent(requireContext(), WheelchairManagementActivity::class.java))
        }

        // 3. Setup Smart Polling
        smartPollingManager = SmartPollingManager.getInstance()
        setupSmartPollingListeners()

        // 4. Setup Observers
        setupObservers(view)

        // 5. Initial Data Fetch
        initializeUserName(view)
        viewModel.fetchPendingItems()
        viewModel.fetchUserCount()
        viewModel.fetchDeviceCount()
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
        // Fixed: ID was changed to home_secoff_fullname in XML
        view.findViewById<TextView>(R.id.secoff_fullname)?.text = fullName
    }

    // This moves the logic from your old Activity to the Fragment
    private fun updateAssistanceCards(pendingItems: List<LocationItem>) {
        assistanceSectionTitle.text = "People currently in need of assistance (${pendingItems.size}):"
        assistanceLayout.removeAllViews()

        if (pendingItems.isEmpty()) {
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

        for (item in pendingItems.take(5)) { // Limit to 5 for home screen performance
            val cardView = LayoutInflater.from(requireContext())
                .inflate(R.layout.assistance_card, assistanceLayout, false)

            setupAssistanceCard(cardView, item, inputFormat, dateFormat, timeFormat)
            assistanceLayout.addView(cardView)
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

        respondButton.setOnClickListener {
            val intent = Intent(requireContext(), MapActivity::class.java).apply {
                putExtra("LOCATION_ID", item.locationID)
                putExtra("LATITUDE", item.latitude)
                putExtra("LONGITUDE", item.longitude)
                putExtra("FULL_NAME", item.fullName)
                putExtra("FLOOR_LEVEL", item.floorLevel)
                putExtra("STATUS", item.status)
            }
            startActivity(intent)
        }
    }

    private fun setupSmartPollingListeners() {
        smartPollingManager.onDataUpdate = {
            viewModel.fetchPendingItems()
            viewModel.fetchUserCount()
            viewModel.fetchDeviceCount()
        }
    }

    override fun onResume() {
        super.onResume()
        smartPollingManager.startPolling()
    }

    override fun onPause() {
        super.onPause()
        smartPollingManager.stopPolling()
    }
}