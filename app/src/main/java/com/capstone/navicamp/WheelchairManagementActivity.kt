package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

data class WheelchairDevice(
    val deviceID: String,
    val userID: Int?,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val floorLevel: String?,
    val connectedUntil: String?,
    val rssi: Int?,
    val distance: Float?,
    val userName: String? = null,
    val maintenanceReason: String? = null,
    val schoolID: String? = null
)

class WheelchairManagementActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var wheelchairsLayout: LinearLayout
    private lateinit var filterSpinner: Spinner
    private lateinit var searchEditText: EditText
    
    private var allWheelchairs = listOf<WheelchairDevice>()
    private var filteredWheelchairs = listOf<WheelchairDevice>()
    private var pollingJob: Job? = null
    private val POLLING_INTERVAL = 10000L // 10 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wheelchair_management)

        setupViews()
        setupSidebar()
        setupFilters()
        loadWheelchairs()
    }

    private fun setupViews() {
        wheelchairsLayout = findViewById(R.id.wheelchairs_layout)
        filterSpinner = findViewById(R.id.filter_spinner)
        searchEditText = findViewById(R.id.search_edit_text)
        
        // Hide refresh button if it exists
        findViewById<Button>(R.id.refresh_button)?.visibility = View.GONE
        
        // Start smart polling
        startSmartPolling()
    }

    private fun setupSidebar() {
        navigationView = findViewById(R.id.navigation_view)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Wheelchair Management"

        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.clear()
                    editor.apply()

                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_item1 -> {
                    val intent = Intent(this, OfficerAccountSettingsFragment::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_item2 -> {
                    val intent = Intent(this, SecurityOfficerActivity::class.java)
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
    }

    private fun setupFilters() {
        val filterOptions = arrayOf("All", "Available", "In Use", "Maintenance")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterSpinner.adapter = adapter

        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                filterWheelchairs()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterWheelchairs()
            }
        })
    }

    private fun loadWheelchairs() {
        findViewById<ProgressBar>(R.id.loading_progress).visibility = View.VISIBLE
        findViewById<TextView>(R.id.no_wheelchairs_text).visibility = View.GONE
        
        lifecycleScope.launch {
            allWheelchairs = withContext(Dispatchers.IO) {
                MySQLHelper.getAllWheelchairs()
            }
            filteredWheelchairs = allWheelchairs
            updateWheelchairCards()
            findViewById<ProgressBar>(R.id.loading_progress).visibility = View.GONE
        }
    }

    private fun filterWheelchairs() {
        val selectedFilter = filterSpinner.selectedItem.toString()
        val searchQuery = searchEditText.text.toString().lowercase().trim()

        filteredWheelchairs = allWheelchairs.filter { wheelchair ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Available" -> wheelchair.status.equals("available", ignoreCase = true)
                "In Use" -> wheelchair.status.equals("in_use", ignoreCase = true) || wheelchair.userID != null
                "Maintenance" -> wheelchair.status.equals("maintenance", ignoreCase = true)
                else -> true
            }

            val matchesSearch = if (searchQuery.isEmpty()) {
                true
            } else {
                wheelchair.deviceID.lowercase().contains(searchQuery) ||
                wheelchair.userName?.lowercase()?.contains(searchQuery) == true ||
                wheelchair.floorLevel?.lowercase()?.contains(searchQuery) == true
            }

            matchesFilter && matchesSearch
        }

        updateWheelchairCards()
    }

    private fun updateWheelchairCards() {
        wheelchairsLayout.removeAllViews()
        
        val noWheelchairsText = findViewById<TextView>(R.id.no_wheelchairs_text)
        val wheelchairCount = findViewById<TextView>(R.id.wheelchair_count)
        
        wheelchairCount.text = "Total Wheelchairs: ${filteredWheelchairs.size}"

        if (filteredWheelchairs.isEmpty()) {
            noWheelchairsText.visibility = View.VISIBLE
            noWheelchairsText.text = if (allWheelchairs.isEmpty()) {
                "No wheelchairs found in the system"
            } else {
                "No wheelchairs match the current filter"
            }
        } else {
            noWheelchairsText.visibility = View.GONE

            for (wheelchair in filteredWheelchairs) {
                val cardView = layoutInflater.inflate(R.layout.wheelchair_card, wheelchairsLayout, false)
                setupWheelchairCard(cardView, wheelchair)
                wheelchairsLayout.addView(cardView)
            }
        }
    }

    private fun setupWheelchairCard(cardView: View, wheelchair: WheelchairDevice) {
        val deviceIdText = cardView.findViewById<TextView>(R.id.device_id_text)
        val statusText = cardView.findViewById<TextView>(R.id.status_text)
        val userNameText = cardView.findViewById<TextView>(R.id.user_name_text)
        val locationText = cardView.findViewById<TextView>(R.id.location_text)
        val connectionText = cardView.findViewById<TextView>(R.id.connection_text)
        val statusIndicator = cardView.findViewById<View>(R.id.status_indicator)
        val viewDetailsButton = cardView.findViewById<Button>(R.id.view_details_button)

        deviceIdText.text = wheelchair.deviceID
        statusText.text = wheelchair.status.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
        }

        if (wheelchair.userName != null && wheelchair.userID != null) {
            userNameText.text = "User: ${wheelchair.userName}"
            userNameText.visibility = View.VISIBLE
        } else {
            userNameText.text = "No user assigned"
            userNameText.visibility = View.VISIBLE
        }

        if (wheelchair.floorLevel != null) {
            locationText.text = "Floor: ${wheelchair.floorLevel}"
        } else {
            locationText.text = "Location: Unknown"
        }

        if (wheelchair.connectedUntil != null) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val connectedDate = dateFormat.parse(wheelchair.connectedUntil)
                val displayFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                connectionText.text = "Connected until: ${displayFormat.format(connectedDate!!)}"
            } catch (e: Exception) {
                connectionText.text = "Connected until: ${wheelchair.connectedUntil}"
            }
        } else {
            connectionText.text = "Not connected"
        }

        when (wheelchair.status.lowercase()) {
            "available" -> statusIndicator.setBackgroundColor(resources.getColor(android.R.color.holo_green_light))
            "in_use" -> statusIndicator.setBackgroundColor(resources.getColor(android.R.color.holo_blue_light))
            "offline" -> statusIndicator.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
            "maintenance" -> statusIndicator.setBackgroundColor(resources.getColor(android.R.color.holo_orange_light))
            else -> statusIndicator.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
        }

        viewDetailsButton.setOnClickListener {
            showWheelchairDetails(wheelchair)
        }
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
        
        dialogView.findViewById<TextView>(R.id.detail_user_id).text = wheelchair.schoolID ?: "None"
        dialogView.findViewById<TextView>(R.id.detail_user_name).text = wheelchair.userName ?: "No user assigned"
        dialogView.findViewById<TextView>(R.id.detail_floor_level).text = wheelchair.floorLevel ?: "Unknown"
        
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

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
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
        val editText = EditText(this).apply {
            hint = "Enter maintenance reason (optional - feature in development)"
            maxLines = 3
            setPadding(16, 16, 16, 16)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
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
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Remove Maintenance Mode")
            .setMessage("This will set the wheelchair back to available status.")
            .setPositiveButton("Remove Maintenance") { _, _ ->
                setDeviceMaintenanceMode(deviceID, false, null, parentDialog)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setDeviceMaintenanceMode(deviceID: String, isMaintenanceMode: Boolean, reason: String?, parentDialog: androidx.appcompat.app.AlertDialog) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.updateDeviceMaintenanceStatus(deviceID, isMaintenanceMode, reason)
            }

            if (success) {
                val message = if (isMaintenanceMode) {
                    "Wheelchair $deviceID set to maintenance mode"
                } else {
                    "Wheelchair $deviceID removed from maintenance mode"
                }
                Toast.makeText(this@WheelchairManagementActivity, message, Toast.LENGTH_SHORT).show()
                
                // Refresh the wheelchair list
                loadWheelchairs()
                
                // Close the parent dialog
                parentDialog.dismiss()
            } else {
                val errorMessage = if (isMaintenanceMode) {
                    "Failed to set maintenance mode"
                } else {
                    "Failed to remove maintenance mode"
                }
                Toast.makeText(this@WheelchairManagementActivity, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDeviceSetup() {
        val intent = Intent(this, SetupActivity::class.java)
        // Add flag to indicate we want to return to main activity after setup
        intent.putExtra("RETURN_TO_MAIN", true)
        startActivity(intent)
    }

    private fun startSmartPolling() {
        pollingJob = lifecycleScope.launch {
            while (true) {
                try {
                    val newWheelchairs = withContext(Dispatchers.IO) {
                        MySQLHelper.getAllWheelchairs()
                    }
                    
                    // Only update if data has changed
                    if (newWheelchairs != allWheelchairs) {
                        allWheelchairs = newWheelchairs
                        filterWheelchairs()
                    }
                } catch (e: Exception) {
                    // Handle error silently, continue polling
                }
                delay(POLLING_INTERVAL)
            }
        }
    }

    private fun stopSmartPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onResume() {
        super.onResume()
        // Update navigation header
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = UserSingleton.fullName
        }
        
        // Restart polling if it was stopped
        if (pollingJob == null) {
            startSmartPolling()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop polling when activity is not visible
        stopSmartPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure polling is stopped
        stopSmartPolling()
    }
} 