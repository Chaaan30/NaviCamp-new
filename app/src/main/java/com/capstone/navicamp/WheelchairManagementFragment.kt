package com.capstone.navicamp

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class WheelchairManagementFragment : Fragment(R.layout.fragment_wheelchair_management) {

    private lateinit var wheelchairsLayout: LinearLayout
    private lateinit var filterSpinner: Spinner
    private lateinit var searchEditText: EditText
    private lateinit var loadingProgress: ProgressBar
    private lateinit var noWheelchairsText: TextView
    private lateinit var wheelchairCountText: TextView

    private var allWheelchairs = listOf<WheelchairDevice>()
    private var filteredWheelchairs = listOf<WheelchairDevice>()
    private var pollingJob: Job? = null
    private val POLLING_INTERVAL = 10000L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views
        wheelchairsLayout = view.findViewById(R.id.wheelchairs_layout)
        filterSpinner = view.findViewById(R.id.filter_spinner)
        searchEditText = view.findViewById(R.id.search_edit_text)
        loadingProgress = view.findViewById(R.id.loading_progress)
        noWheelchairsText = view.findViewById(R.id.no_wheelchairs_text)
        wheelchairCountText = view.findViewById(R.id.wheelchair_count)

        setupFilters()
        loadWheelchairs()
    }

    private fun setupFilters() {
        val filterOptions = arrayOf("All", "Available", "In Use", "Maintenance")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterSpinner.adapter = adapter

        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { filterWheelchairs() }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { filterWheelchairs() }
        })
    }

    private fun loadWheelchairs() {
        loadingProgress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            allWheelchairs = withContext(Dispatchers.IO) { MySQLHelper.getAllWheelchairs() }
            filteredWheelchairs = allWheelchairs
            updateWheelchairCards()
            loadingProgress.visibility = View.GONE
        }
    }

    private fun filterWheelchairs() {
        val selectedFilter = filterSpinner.selectedItem.toString()
        val searchQuery = searchEditText.text.toString().lowercase().trim()

        filteredWheelchairs = allWheelchairs.filter { wheelchair ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Available" -> wheelchair.status.equals("available", true)
                "In Use" -> wheelchair.status.equals("in_use", true) || wheelchair.userID != null
                "Maintenance" -> wheelchair.status.equals("maintenance", true)
                else -> true
            }
            val matchesSearch = searchQuery.isEmpty() || wheelchair.deviceID.lowercase().contains(searchQuery)
            matchesFilter && matchesSearch
        }
        updateWheelchairCards()
    }

    private fun updateWheelchairCards() {
        wheelchairsLayout.removeAllViews()
        wheelchairCountText.text = "Total Wheelchairs: ${filteredWheelchairs.size}"

        if (filteredWheelchairs.isEmpty()) {
            noWheelchairsText.visibility = View.VISIBLE
        } else {
            noWheelchairsText.visibility = View.GONE
            for (wheelchair in filteredWheelchairs) {
                val cardView = layoutInflater.inflate(R.layout.wheelchair_card, wheelchairsLayout, false)
                setupWheelchairCard(cardView, wheelchair)
                wheelchairsLayout.addView(cardView)
            }
        }
    }

    private fun setupWheelchairCard(view: View, wheelchair: WheelchairDevice) {
        view.findViewById<TextView>(R.id.device_id_text).text = wheelchair.deviceID
        val statusText = view.findViewById<TextView>(R.id.status_text)
        statusText.text = wheelchair.status.uppercase()

        view.findViewById<Button>(R.id.view_details_button).setOnClickListener {
            showWheelchairDetails(wheelchair)
        }

        val indicator = view.findViewById<View>(R.id.status_indicator)
        val color = when(wheelchair.status.lowercase()) {
            "available" -> android.R.color.holo_green_light
            "in_use" -> android.R.color.holo_blue_light
            "maintenance" -> android.R.color.holo_orange_light
            else -> android.R.color.darker_gray
        }
        indicator.setBackgroundColor(resources.getColor(color, null))
    }

    private fun showWheelchairDetails(wheelchair: WheelchairDevice) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wheelchair_details, null)

        dialogView.findViewById<TextView>(R.id.detail_device_id).text = wheelchair.deviceID

        // Update status badge
        val statusBadge = dialogView.findViewById<TextView>(R.id.detail_status_badge)
        statusBadge.text = wheelchair.status.uppercase()

        // Set status badge color based on status
        when (wheelchair.status.lowercase()) {
            "available" -> statusBadge.setBackgroundColor(resources.getColor(android.R.color.holo_green_light))
            "in_use" -> statusBadge.setBackgroundColor(resources.getColor(android.R.color.holo_blue_light))
            "offline" -> statusBadge.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
            "maintenance" -> statusBadge.setBackgroundColor(resources.getColor(R.color.orange))
            else -> statusBadge.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
        }

        dialogView.findViewById<TextView>(R.id.detail_user_id).text = wheelchair.userID?.toString() ?: "None"
        dialogView.findViewById<TextView>(R.id.detail_user_name).text = wheelchair.userName ?: "No user assigned"
        dialogView.findViewById<TextView>(R.id.detail_floor_level).text = wheelchair.floorLevel ?: "Unknown"
        dialogView.findViewById<TextView>(R.id.detail_latitude).text = wheelchair.latitude?.toString() ?: "N/A"
        dialogView.findViewById<TextView>(R.id.detail_longitude).text = wheelchair.longitude?.toString() ?: "N/A"
        dialogView.findViewById<TextView>(R.id.detail_rssi).text = wheelchair.rssi?.toString() ?: "N/A"
        dialogView.findViewById<TextView>(R.id.detail_distance).text = wheelchair.distance?.toString() ?: "N/A"

        // Format connected until date
        val connectedUntilText = if (wheelchair.connectedUntil != null) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val connectedDate = dateFormat.parse(wheelchair.connectedUntil)
                val displayFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                displayFormat.format(connectedDate!!)
            } catch (e: Exception) {
                wheelchair.connectedUntil
            }
        } else {
            "Not connected"
        }
        dialogView.findViewById<TextView>(R.id.detail_connected_until).text = connectedUntilText

        // Handle maintenance information
        val maintenanceReasonLayout = dialogView.findViewById<LinearLayout>(R.id.maintenance_reason_layout)
        val maintenanceReasonText = dialogView.findViewById<TextView>(R.id.detail_maintenance_reason)
        val setMaintenanceBtn = dialogView.findViewById<MaterialButton>(R.id.btn_set_maintenance)
        val removeMaintenanceBtn = dialogView.findViewById<MaterialButton>(R.id.btn_remove_maintenance)

        if (wheelchair.status.equals("maintenance", ignoreCase = true)) {
            // Device is in maintenance mode
            maintenanceReasonLayout.visibility = View.VISIBLE
            maintenanceReasonText.text = wheelchair.maintenanceReason ?: "Maintenance reason not available (feature in development)"
            setMaintenanceBtn.visibility = View.GONE
            removeMaintenanceBtn.visibility = View.VISIBLE
        } else {
            // Device is not in maintenance mode
            maintenanceReasonLayout.visibility = View.GONE
            setMaintenanceBtn.visibility = View.VISIBLE
            removeMaintenanceBtn.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Set maintenance button click listener
        setMaintenanceBtn.setOnClickListener {
            showSetMaintenanceDialog(wheelchair.deviceID, dialog)
        }

        // Remove maintenance button click listener
        removeMaintenanceBtn.setOnClickListener {
            showRemoveMaintenanceDialog(wheelchair.deviceID, dialog)
        }

        dialog.show()
    }

    private fun showSetMaintenanceDialog(deviceID: String, parentDialog: androidx.appcompat.app.AlertDialog) {
        val editText = EditText(requireContext()).apply {
            hint = "Enter maintenance reason (optional - feature in development)"
            maxLines = 3
            setPadding(16, 16, 16, 16)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Set Maintenance Mode")
            .setMessage("This will disconnect any current user and set the wheelchair to maintenance mode.\n\nNote: Maintenance reason storage is currently in development.")
            .setView(editText)
            .setPositiveButton("Set Maintenance") { _, _ ->
                val reason = editText.text.toString().trim()
                setDeviceMaintenanceMode(deviceID, true, reason, parentDialog)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveMaintenanceDialog(deviceID: String, parentDialog: androidx.appcompat.app.AlertDialog) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Remove Maintenance Mode")
            .setMessage("This will set the wheelchair back to available status.")
            .setPositiveButton("Remove Maintenance") { _, _ ->
                setDeviceMaintenanceMode(deviceID, false, null, parentDialog)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setDeviceMaintenanceMode(deviceID: String, isMode: Boolean, reason: String?, dialog: AlertDialog) {
        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) { MySQLHelper.updateDeviceMaintenanceStatus(deviceID, isMode, reason) }
            if (success) {
                loadWheelchairs()
                dialog.dismiss()
            }
        }
    }

    private fun startSmartPolling() {
        pollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val newOnes = withContext(Dispatchers.IO) { MySQLHelper.getAllWheelchairs() }
                if (newOnes != allWheelchairs) {
                    allWheelchairs = newOnes
                    filterWheelchairs()
                }
                delay(POLLING_INTERVAL)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startSmartPolling()
    }

    override fun onPause() {
        super.onPause()
        pollingJob?.cancel()
    }
}