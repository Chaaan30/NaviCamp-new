package com.capstone.navicamp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.util.Patterns
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Build
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.*
import java.util.Properties
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeBodyPart
import javax.activation.FileDataSource
import javax.activation.DataHandler
import android.app.Dialog

class IncidentLogNew : AppCompatActivity() {
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    
    // UI Components
    private lateinit var incidentsRecyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var noIncidentsText: TextView
    private lateinit var filterButton: MaterialButton
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
    private var selectedLocationFilter = "All Locations"
    private var customStartDate: Calendar? = null
    private var customEndDate: Calendar? = null
    private var selectedExportFormat = "excel"
    private var pendingEmailAddress: String? = null
    
    companion object {
        private const val CREATE_FILE_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incident_log_new)

        setupViews()
        setupSidebar() 
        setupRecyclerView()
        setupObservers()
        loadIncidents()
    }

    private fun setupViews() {
        // Find all views
        incidentsRecyclerView = findViewById(R.id.incidents_recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        noIncidentsText = findViewById(R.id.no_incidents_text)
        filterButton = findViewById(R.id.filter_button)
        exportButton = findViewById(R.id.export_button)
        
        // Summary cards (only total and resolved)
        totalIncidentsCount = findViewById(R.id.total_incidents_count)
        resolvedIncidentsCount = findViewById(R.id.resolved_incidents_count)
        
        // Setup buttons
        filterButton.setOnClickListener {
            showFilterDialog()
        }
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

    private fun showFilterDialog() {
        val dialog = Dialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_filter_options, null)
        dialog.setContentView(dialogView)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        var tempSelectedDate = selectedDateFilter
        var tempSelectedStatus = selectedStatusFilter
        var tempSelectedLocation = selectedLocationFilter
        var tempCustomStart = customStartDate
        var tempCustomEnd = customEndDate

        val chipAllTime = dialogView.findViewById<Chip>(R.id.dialog_chip_all_time)
        val chipToday = dialogView.findViewById<Chip>(R.id.dialog_chip_today)
        val chipThisWeek = dialogView.findViewById<Chip>(R.id.dialog_chip_this_week)
        val chipThisMonth = dialogView.findViewById<Chip>(R.id.dialog_chip_this_month)
        val chipThisYear = dialogView.findViewById<Chip>(R.id.dialog_chip_this_year)
        val chipCustomDate = dialogView.findViewById<Chip>(R.id.dialog_chip_custom_date)

        val dateChips = listOf(chipAllTime, chipToday, chipThisWeek, chipThisMonth, chipThisYear, chipCustomDate)

        val chipAllStatus = dialogView.findViewById<Chip>(R.id.dialog_chip_all_status)
        val chipPending = dialogView.findViewById<Chip>(R.id.dialog_chip_pending)
        val chipOngoing = dialogView.findViewById<Chip>(R.id.dialog_chip_ongoing)
        val chipResolved = dialogView.findViewById<Chip>(R.id.dialog_chip_resolved)
        val chipFalseAlarm = dialogView.findViewById<Chip>(R.id.dialog_chip_false_alarm)

        val statusChips = listOf(chipAllStatus, chipPending, chipOngoing, chipResolved, chipFalseAlarm)
        
        // Pending and False Alarm status options are not valid/available for Admin in IncidentLogNew
        chipPending.visibility = View.GONE
        chipFalseAlarm.visibility = View.GONE

        val chipAllLocations = dialogView.findViewById<Chip>(R.id.dialog_chip_all_locations)
        val chipLocationGym = dialogView.findViewById<Chip>(R.id.dialog_chip_location_gym)
        val chipLocationField = dialogView.findViewById<Chip>(R.id.dialog_chip_location_field)
        val chipLocationLounge = dialogView.findViewById<Chip>(R.id.dialog_chip_location_student_lounge)
        val chipLocationClinic = dialogView.findViewById<Chip>(R.id.dialog_chip_location_clinic)
        val chipLocationOther = dialogView.findViewById<Chip>(R.id.dialog_chip_location_other)

        val locationChips = listOf(chipAllLocations, chipLocationGym, chipLocationField, chipLocationLounge, chipLocationClinic, chipLocationOther)

        fun selectDateChip(selected: Chip) {
            dateChips.forEach { it.isChecked = (it == selected) }
        }
        fun selectStatusChip(selected: Chip) {
            statusChips.forEach { it.isChecked = (it == selected) }
        }
        fun selectLocationChip(selected: Chip) {
            locationChips.forEach { it.isChecked = (it == selected) }
        }

        // Initialize dialog chips state
        when (tempSelectedDate) {
            "All Time" -> selectDateChip(chipAllTime)
            "Today" -> selectDateChip(chipToday)
            "This Week" -> selectDateChip(chipThisWeek)
            "This Month" -> selectDateChip(chipThisMonth)
            "This Year" -> selectDateChip(chipThisYear)
            "Custom Range" -> {
                selectDateChip(chipCustomDate)
                if (tempCustomStart != null && tempCustomEnd != null) {
                    chipCustomDate.text = "Custom (${formatDateShort(tempCustomStart!!)} - ${formatDateShort(tempCustomEnd!!)})"
                }
            }
        }

        when (tempSelectedStatus) {
            "All Status" -> selectStatusChip(chipAllStatus)
            "Ongoing" -> selectStatusChip(chipOngoing)
            "Resolved" -> selectStatusChip(chipResolved)
        }

        when (tempSelectedLocation) {
            "All Locations" -> selectLocationChip(chipAllLocations)
            "Gym" -> selectLocationChip(chipLocationGym)
            "Field" -> selectLocationChip(chipLocationField)
            "Student Lounge" -> selectLocationChip(chipLocationLounge)
            "Clinic" -> selectLocationChip(chipLocationClinic)
            "Other" -> selectLocationChip(chipLocationOther)
        }

        chipAllTime.setOnClickListener { tempSelectedDate = "All Time"; selectDateChip(chipAllTime) }
        chipToday.setOnClickListener { tempSelectedDate = "Today"; selectDateChip(chipToday) }
        chipThisWeek.setOnClickListener { tempSelectedDate = "This Week"; selectDateChip(chipThisWeek) }
        chipThisMonth.setOnClickListener { tempSelectedDate = "This Month"; selectDateChip(chipThisMonth) }
        chipThisYear.setOnClickListener { tempSelectedDate = "This Year"; selectDateChip(chipThisYear) }
        chipCustomDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                val startCal = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                DatePickerDialog(this, { _, endYear, endMonth, endDay ->
                    val endCal = Calendar.getInstance().apply {
                        set(endYear, endMonth, endDay, 23, 59, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    tempCustomStart = startCal
                    tempCustomEnd = endCal
                    tempSelectedDate = "Custom Range"
                    chipCustomDate.text = "Custom (${formatDateShort(startCal)} - ${formatDateShort(endCal)})"
                    selectDateChip(chipCustomDate)
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                setTitle("Select Start Date")
            }.show()
        }

        chipAllStatus.setOnClickListener { tempSelectedStatus = "All Status"; selectStatusChip(chipAllStatus) }
        chipOngoing.setOnClickListener { tempSelectedStatus = "Ongoing"; selectStatusChip(chipOngoing) }
        chipResolved.setOnClickListener { tempSelectedStatus = "Resolved"; selectStatusChip(chipResolved) }

        chipAllLocations.setOnClickListener { tempSelectedLocation = "All Locations"; selectLocationChip(chipAllLocations) }
        chipLocationGym.setOnClickListener { tempSelectedLocation = "Gym"; selectLocationChip(chipLocationGym) }
        chipLocationField.setOnClickListener { tempSelectedLocation = "Field"; selectLocationChip(chipLocationField) }
        chipLocationLounge.setOnClickListener { tempSelectedLocation = "Student Lounge"; selectLocationChip(chipLocationLounge) }
        chipLocationClinic.setOnClickListener { tempSelectedLocation = "Clinic"; selectLocationChip(chipLocationClinic) }
        chipLocationOther.setOnClickListener { tempSelectedLocation = "Other"; selectLocationChip(chipLocationOther) }

        dialogView.findViewById<View>(R.id.btn_clear_filters).setOnClickListener {
            selectedDateFilter = "All Time"
            selectedStatusFilter = "All Status"
            selectedLocationFilter = "All Locations"
            customStartDate = null
            customEndDate = null
            filterIncidents()
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btn_apply_filters).setOnClickListener {
            selectedDateFilter = tempSelectedDate
            selectedStatusFilter = tempSelectedStatus
            selectedLocationFilter = tempSelectedLocation
            customStartDate = tempCustomStart
            customEndDate = tempCustomEnd
            filterIncidents()
            dialog.dismiss()
        }

        dialog.show()
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
            if (row.size >= 15) {
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
                    incidentDescription = row[10],
                    userType = row[11],
                    department = row[12],
                    actionFA = row[13],
                    actionINFO = row[14],
                    relocatedLocation = row[10]
                )
            } else if (row.size >= 11) {
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
            
            // Relocation location filter
            val matchesLocation = when (selectedLocationFilter) {
                "All Locations" -> true
                "Other" -> {
                    val fixedOptions = listOf("gym", "field", "student lounge", "clinic")
                    val loc = incident.relocatedLocation.trim().lowercase(Locale.getDefault())
                    loc.isNotBlank() && fixedOptions.none { it == loc }
                }
                else -> incident.relocatedLocation.trim().equals(selectedLocationFilter, ignoreCase = true)
            }
            
            matchesStatus && matchesDate && matchesLocation
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
        val options = arrayOf("Excel (.xlsx)", "PDF (.pdf)", "Email / Share (Excel + PDF)")
        AlertDialog.Builder(this)
            .setTitle("Export Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportIncidents("excel")
                    1 -> exportIncidents("pdf") 
                    2 -> sendReportsByEmail()
                }
            }
            .show()
    }

    private fun sendReportsByEmail() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dataToExport = getSortedExportData()
                val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                val officerName = sharedPreferences.getString("fullName", "Officer") ?: "Officer"
                val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val randomSuffix = String.format("%04d", java.util.Random().nextInt(10000))
                val excelFile = java.io.File(cacheDir, "assistance_data_${dateStamp}_${randomSuffix}.xlsx")
                val pdfFile = java.io.File(cacheDir, "assistance_data_${dateStamp}_${randomSuffix}.pdf")

                excelFile.outputStream().use { out ->
                    ExcelExportUtils.exportIncidentDataToExcelStream(out, dataToExport, officerName)
                }

                pdfFile.outputStream().use { out ->
                    PdfExportUtils.exportIncidentDataToPdfStream(out, dataToExport, officerName)
                }

                withContext(Dispatchers.Main) {
                    try {
                        val excelUri = androidx.core.content.FileProvider.getUriForFile(
                            this@IncidentLogNew,
                            "$packageName.fileprovider",
                            excelFile
                        )
                        val pdfUri = androidx.core.content.FileProvider.getUriForFile(
                            this@IncidentLogNew,
                            "$packageName.fileprovider",
                            pdfFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putExtra(Intent.EXTRA_SUBJECT, "Incident Assistance Report - ${dateStamp}_${randomSuffix}")
                            putExtra(Intent.EXTRA_TEXT, "Please find attached the Incident Assistance Report exported by $officerName on ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())}.")
                            val uriList = ArrayList<android.net.Uri>()
                            uriList.add(excelUri)
                            uriList.add(pdfUri)
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share Report via Email"))
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        Toast.makeText(this@IncidentLogNew, "Failed to open email app: ${ex.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@IncidentLogNew, "Failed to prepare files: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getSortedExportData(): List<List<String>> {
        val sorted = filteredIncidents.sortedByDescending { incident ->
            try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                format.parse(incident.timeOfAlert)?.time ?: 0L
            } catch (e: Exception) { 0L }
        }
        return sorted.map { incident ->
            listOf(
                incident.alertId,
                incident.userId,
                incident.deviceId,
                incident.userName,
                incident.coordinates,
                incident.floorLevel,
                incident.status,
                getAssistanceTypeForExport(incident),
                incident.timeOfAlert,
                incident.resolvedOn ?: "",
                incident.officerName ?: "",
                incident.actionFA,
                incident.actionINFO,
                incident.relocatedLocation
            )
        }
    }

    private fun exportIncidents(format: String) {
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val randomSuffix = String.format("%04d", java.util.Random().nextInt(10000))
        selectedExportFormat = format
        
        val (mimeType, extension) = when (format) {
            "pdf" -> Pair("application/pdf", "pdf")
            "excel" -> Pair("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
            else -> Pair("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
        }
        
        val createFileIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, "assistance_data_${dateStamp}_${randomSuffix}.${extension}")
        }
        startActivityForResult(createFileIntent, CREATE_FILE_REQUEST_CODE)
    }

    private fun getAssistanceTypeForExport(incident: IncidentCardAdapter.IncidentData): String {
        val combinedText = listOf(
            incident.incidentDescription,
            incident.relocatedLocation,
            incident.actionINFO
        ).joinToString(" ").lowercase(Locale.getDefault())

        return if (combinedText.contains("fall")) {
            "Fall Detection"
        } else {
            "Manual SOS"
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
            val dataToExport = getSortedExportData()
            
            val officerName = (navigationView.getHeaderView(0).findViewById<TextView>(R.id.nav_name_header).text as String?) ?: "Officer"
            
            if (uri != null) {
                try {
                    when (selectedExportFormat) {
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