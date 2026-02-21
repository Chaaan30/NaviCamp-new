package com.capstone.navicamp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.navigation.NavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import android.os.Build
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.*

class IncidentLogNew : AppCompatActivity() {
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    
    // UI Components - Individual Chips (no longer in ChipGroups)
    private lateinit var incidentsRecyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var noIncidentsText: TextView
    private lateinit var exportButton: MaterialButton
    
    // Summary Cards (Only Total and Resolved)
    private lateinit var totalIncidentsCount: TextView
    private lateinit var resolvedIncidentsCount: TextView
    
    // Adapter and Data
    private lateinit var incidentAdapter: IncidentCardAdapter
    private val viewModel: IncidentLogViewModel by viewModels()
    private var allIncidents = listOf<IncidentCardAdapter.IncidentData>()
    private var filteredIncidents = listOf<IncidentCardAdapter.IncidentData>()
    
    // Filter State
    private var selectedDateFilter = "All Time"
    private var selectedStatusFilter = "All Status"
    private var customStartDate: Calendar? = null
    private var customEndDate: Calendar? = null
    private var selectedExportFormat = "csv"
    
    companion object {
        private const val CREATE_FILE_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incident_log_new)

        setupViews()
        setupSidebar() 
        setupRecyclerView()
        setupFilters()
        setupObservers()
        loadIncidents()
    }

    private fun setupViews() {
        // Find all views
        incidentsRecyclerView = findViewById(R.id.incidents_recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        noIncidentsText = findViewById(R.id.no_incidents_text)
        exportButton = findViewById(R.id.export_button)
        
        // Summary cards (only total and resolved)
        totalIncidentsCount = findViewById(R.id.total_incidents_count)
        resolvedIncidentsCount = findViewById(R.id.resolved_incidents_count)
        
        // Setup export button
        exportButton.setOnClickListener {
            showExportOptionsDialog()
        }
    }

    private fun setupSidebar() {
        navigationView = findViewById(R.id.navigation_view)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Incident Log"

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
                else -> false
            }
        }
        
        // Update navigation header - use SharedPreferences as fallback
        val headerView = navigationView.getHeaderView(0)
        val fullName = if (UserSingleton.fullName.isNullOrBlank()) {
            val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            val storedName = sharedPreferences.getString("fullName", "Officer")
            UserSingleton.fullName = storedName // Update singleton
            storedName
        } else {
            UserSingleton.fullName
        } ?: "Officer"
        headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = fullName
    }

    private fun setupRecyclerView() {
        incidentAdapter = IncidentCardAdapter(
            context = this,
            incidents = emptyList(),
            onMapClick = { incident ->
                // Removed view map functionality as requested
                Toast.makeText(this, "Incident location: ${incident.coordinates}", Toast.LENGTH_SHORT).show()
            },
            onResolveClick = { incident ->
                resolveIncident(incident)
            }
        )
        
        incidentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@IncidentLogNew)
            adapter = incidentAdapter
        }
    }

    private fun setupFilters() {
        // Setup Date Filter Chips
        setupDateFilterChips()
        
        // Setup Status Filter Chips  
        setupStatusFilterChips()
    }
    
    private fun setupDateFilterChips() {
        findViewById<Chip>(R.id.chip_all_time).setOnClickListener { 
            selectDateFilter("All Time")
        }
        findViewById<Chip>(R.id.chip_today).setOnClickListener { 
            selectDateFilter("Today")
        }
        findViewById<Chip>(R.id.chip_this_week).setOnClickListener { 
            selectDateFilter("This Week")
        }
        findViewById<Chip>(R.id.chip_this_month).setOnClickListener { 
            selectDateFilter("This Month")
        }
        findViewById<Chip>(R.id.chip_this_year).setOnClickListener { 
            selectDateFilter("This Year")
        }
        findViewById<Chip>(R.id.chip_custom_date).setOnClickListener { 
            showCustomDateRangePicker()
        }
    }
    
    private fun selectDateFilter(filter: String) {
        // Uncheck all date chips
        findViewById<Chip>(R.id.chip_all_time).isChecked = false
        findViewById<Chip>(R.id.chip_today).isChecked = false
        findViewById<Chip>(R.id.chip_this_week).isChecked = false
        findViewById<Chip>(R.id.chip_this_month).isChecked = false
        findViewById<Chip>(R.id.chip_this_year).isChecked = false
        findViewById<Chip>(R.id.chip_custom_date).isChecked = false
        
        // Check the selected chip
        when (filter) {
            "All Time" -> findViewById<Chip>(R.id.chip_all_time).isChecked = true
            "Today" -> findViewById<Chip>(R.id.chip_today).isChecked = true
            "This Week" -> findViewById<Chip>(R.id.chip_this_week).isChecked = true
            "This Month" -> findViewById<Chip>(R.id.chip_this_month).isChecked = true
            "This Year" -> findViewById<Chip>(R.id.chip_this_year).isChecked = true
            "Custom Range" -> findViewById<Chip>(R.id.chip_custom_date).isChecked = true
        }
        
        selectedDateFilter = filter
        filterIncidents()
    }
    
    private fun setupStatusFilterChips() {
        findViewById<Chip>(R.id.chip_all_status).setOnClickListener { 
            selectStatusFilter("All Status")
        }
                findViewById<Chip>(R.id.chip_ongoing).setOnClickListener {
            selectStatusFilter("Ongoing")
        }
        findViewById<Chip>(R.id.chip_resolved).setOnClickListener { 
            selectStatusFilter("Resolved")
        }
    }
    
    private fun selectStatusFilter(filter: String) {
        // Uncheck all status chips
        findViewById<Chip>(R.id.chip_all_status).isChecked = false
        findViewById<Chip>(R.id.chip_ongoing).isChecked = false
        findViewById<Chip>(R.id.chip_resolved).isChecked = false
        
        // Check the selected chip
        when (filter) {
            "All Status" -> findViewById<Chip>(R.id.chip_all_status).isChecked = true
            "Ongoing" -> findViewById<Chip>(R.id.chip_ongoing).isChecked = true
            "Resolved" -> findViewById<Chip>(R.id.chip_resolved).isChecked = true
        }
        
        selectedStatusFilter = filter
        filterIncidents()
    }
    
    private fun showCustomDateRangePicker() {
        val calendar = Calendar.getInstance()
        
        // Start Date Picker
        DatePickerDialog(this, { _, year, month, day ->
            customStartDate = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            // End Date Picker
            DatePickerDialog(this, { _, endYear, endMonth, endDay ->
                customEndDate = Calendar.getInstance().apply {
                    set(endYear, endMonth, endDay, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                
                findViewById<Chip>(R.id.chip_custom_date).text = "Custom (${formatDateShort(customStartDate!!)} - ${formatDateShort(customEndDate!!)})"
                selectDateFilter("Custom Range")
                
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
                .show()
                
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            .apply {
                setTitle("Select Start Date")
            }.show()
    }
    
    private fun formatDateShort(calendar: Calendar): String {
        val format = SimpleDateFormat("MM/dd", Locale.getDefault())
        return format.format(calendar.time)
    }

    private fun setupObservers() {
        viewModel.incidentData.observe(this) { data ->
            loadingProgress.visibility = View.GONE
            
            // Convert raw data to IncidentData objects
            allIncidents = convertToIncidentData(data)
            filterIncidents()
            updateSummaryCards()
        }
    }

    private fun loadIncidents() {
        loadingProgress.visibility = View.VISIBLE
        noIncidentsText.visibility = View.GONE
        viewModel.fetchIncidentData()
    }

    private fun convertToIncidentData(rawData: List<List<String>>): List<IncidentCardAdapter.IncidentData> {
        return rawData.mapNotNull { row ->
            if (row.size >= 11) {
                IncidentCardAdapter.IncidentData(
                    alertId = row[0],
                    userId = row[1],
                    deviceId = row[2],
                    userName = row[3],
                    coordinates = row[4],
                    floorLevel = row[5],
                    status = row[6],
                    timeOfAlert = row[7],
                    resolvedOn = row[8].takeIf { it.isNotBlank() },
                    officerName = row[9].takeIf { it.isNotBlank() },
                    incidentDescription = row[10]
                )
            } else {
                null
            }
        }
    }

    private fun filterIncidents() {
        filteredIncidents = allIncidents.filter { incident ->
            // Status filter
            val matchesStatus = when (selectedStatusFilter) {
                "All Status" -> true
                "Ongoing" -> incident.status.equals("ongoing", ignoreCase = true)
                "Resolved" -> incident.status.equals("resolved", ignoreCase = true)
                else -> true
            }
            
            // Date filters with enhanced options
            val matchesDate = when (selectedDateFilter) {
                "All Time" -> true
                "Today" -> isToday(incident.timeOfAlert)
                "This Week" -> isThisWeek(incident.timeOfAlert)
                "This Month" -> isThisMonth(incident.timeOfAlert)
                "This Year" -> isThisYear(incident.timeOfAlert)
                "Custom Range" -> isInCustomRange(incident.timeOfAlert)
                else -> true
            }
            
            matchesStatus && matchesDate
        }
        
        updateIncidentsList()
    }

    private fun updateIncidentsList() {
        if (filteredIncidents.isEmpty()) {
            noIncidentsText.visibility = View.VISIBLE
            noIncidentsText.text = if (allIncidents.isEmpty()) {
                "No incidents found"
            } else {
                "No incidents match the current filters"
            }
        } else {
            noIncidentsText.visibility = View.GONE
        }
        
        incidentAdapter.updateIncidents(filteredIncidents)
    }

    private fun updateSummaryCards() {
        totalIncidentsCount.text = allIncidents.size.toString()
        
        val resolvedCount = allIncidents.count { it.status.equals("resolved", ignoreCase = true) }
        resolvedIncidentsCount.text = resolvedCount.toString()
    }

    private fun resolveIncident(incident: IncidentCardAdapter.IncidentData) {
        lifecycleScope.launch {
            // TODO: Implement incident resolution in database
            Toast.makeText(this@IncidentLogNew, "Marking ${incident.alertId} as resolved", Toast.LENGTH_SHORT).show()
            
            // Refresh data after resolution
            loadIncidents()
        }
    }

    private fun showExportOptionsDialog() {
        val options = arrayOf("CSV", "PDF", "Excel")
        AlertDialog.Builder(this)
            .setTitle("Export Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportIncidents("csv")
                    1 -> exportIncidents("pdf") 
                    2 -> exportIncidents("excel")
                }
            }
            .show()
    }

    private fun exportIncidents(format: String) {
        val dataToExport = filteredIncidents.map { incident ->
            listOf(
                incident.alertId,
                incident.userId,
                incident.deviceId,
                incident.userName,
                incident.coordinates,
                incident.floorLevel,
                incident.status,
                incident.timeOfAlert,
                incident.resolvedOn ?: "",
                incident.officerName ?: "",
                incident.incidentDescription
            )
        }
        
        val officerName = (navigationView.getHeaderView(0).findViewById<TextView>(R.id.nav_name_header).text as String?) ?: "Officer"
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        
        // Store the format for use in onActivityResult
        selectedExportFormat = format
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): Use SAF
            val (mimeType, extension) = when (format) {
                "csv" -> Pair("text/csv", "csv")
                "pdf" -> Pair("application/pdf", "pdf")
                "excel" -> Pair("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
                else -> Pair("text/csv", "csv")
            }
            
            val createFileIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, "assistance_data_${dateStamp}.${extension}")
            }
            startActivityForResult(createFileIntent, CREATE_FILE_REQUEST_CODE)
        } else {
            // Android 10 and below
            when (format) {
                "csv" -> {
                    val file = exportIncidentDataToCSV(this, dataToExport)
                    if (file != null) {
                        Toast.makeText(this, "CSV exported successfully to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "CSV export failed", Toast.LENGTH_SHORT).show()
                    }
                }
                "pdf", "excel" -> {
                    Toast.makeText(this, "${format.uppercase()} export not implemented for Android 10 and below", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Enhanced date filtering functions
    private fun isToday(dateString: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val incidentDate = format.parse(dateString)
            val today = Calendar.getInstance()
            val incidentCal = Calendar.getInstance()
            
            if (incidentDate != null) {
                incidentCal.time = incidentDate
                today.get(Calendar.YEAR) == incidentCal.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == incidentCal.get(Calendar.DAY_OF_YEAR)
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun isThisWeek(dateString: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val incidentDate = format.parse(dateString)
            val today = Calendar.getInstance()
            val incidentCal = Calendar.getInstance()
            
            if (incidentDate != null) {
                incidentCal.time = incidentDate
                today.get(Calendar.YEAR) == incidentCal.get(Calendar.YEAR) &&
                today.get(Calendar.WEEK_OF_YEAR) == incidentCal.get(Calendar.WEEK_OF_YEAR)
            } else false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isThisMonth(dateString: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val incidentDate = format.parse(dateString)
            val today = Calendar.getInstance()
            val incidentCal = Calendar.getInstance()
            
            if (incidentDate != null) {
                incidentCal.time = incidentDate
                today.get(Calendar.YEAR) == incidentCal.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == incidentCal.get(Calendar.MONTH)
            } else false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isThisYear(dateString: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val incidentDate = format.parse(dateString)
            val today = Calendar.getInstance()
            val incidentCal = Calendar.getInstance()
            
            if (incidentDate != null) {
                incidentCal.time = incidentDate
                today.get(Calendar.YEAR) == incidentCal.get(Calendar.YEAR)
            } else false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isInCustomRange(dateString: String): Boolean {
        if (customStartDate == null || customEndDate == null) return false
        
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val incidentDate = format.parse(dateString)
            
            if (incidentDate != null) {
                incidentDate.time >= customStartDate!!.timeInMillis &&
                incidentDate.time <= customEndDate!!.timeInMillis
            } else false
        } catch (e: Exception) {
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            val dataToExport = filteredIncidents.map { incident ->
                listOf(
                    incident.alertId,
                    incident.userId,
                    incident.deviceId,
                    incident.userName,
                    incident.coordinates,
                    incident.floorLevel,
                    incident.status,
                    incident.timeOfAlert,
                    incident.resolvedOn ?: "",
                    incident.officerName ?: "",
                    incident.incidentDescription
                )
            }
            
            val officerName = (navigationView.getHeaderView(0).findViewById<TextView>(R.id.nav_name_header).text as String?) ?: "Officer"
            
            if (uri != null) {
                try {
                    when (selectedExportFormat) {
                        "csv" -> {
                            CsvExportUtils.exportIncidentDataToCsv(this, uri, dataToExport, officerName)
                            Toast.makeText(this, "CSV exported successfully", Toast.LENGTH_LONG).show()
                        }
                        "pdf" -> {
                            PdfExportUtils.exportIncidentDataToPdf(this, uri, dataToExport, officerName)
                            Toast.makeText(this, "PDF exported successfully", Toast.LENGTH_LONG).show()
                        }
                        "excel" -> {
                            ExcelExportUtils.exportIncidentDataToExcel(this, uri, dataToExport, officerName)
                            Toast.makeText(this, "Excel exported successfully", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Toast.makeText(this, "Unknown export format", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Export failed: No file selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadIncidents()
        
        // Update navigation header - use SharedPreferences as fallback
        val headerView = navigationView.getHeaderView(0)
        val fullName = if (UserSingleton.fullName.isNullOrBlank()) {
            val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            val storedName = sharedPreferences.getString("fullName", "Officer")
            UserSingleton.fullName = storedName // Update singleton
            storedName
        } else {
            UserSingleton.fullName
        } ?: "Officer"
        headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = fullName
    }
} 