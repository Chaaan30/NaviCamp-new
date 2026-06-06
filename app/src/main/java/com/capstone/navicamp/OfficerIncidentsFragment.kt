package com.capstone.navicamp

import android.app.Activity.RESULT_OK
import android.app.DatePickerDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

class OfficerIncidentsFragment : Fragment(R.layout.fragment_officer_incidents) {

    // UI Components
    private lateinit var incidentsRecyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var noIncidentsText: TextView
    private lateinit var filterButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private lateinit var totalIncidentsCount: TextView
    private lateinit var ongoingIncidentsCount: TextView
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupRecyclerView()
        setupObservers()
        loadIncidents()
    }

    private fun setupViews(view: View) {
        incidentsRecyclerView = view.findViewById(R.id.incidents_recycler_view)
        loadingProgress = view.findViewById(R.id.loading_progress)
        noIncidentsText = view.findViewById(R.id.no_incidents_text)
        filterButton = view.findViewById(R.id.filter_button)
        exportButton = view.findViewById(R.id.export_button)
        totalIncidentsCount = view.findViewById(R.id.total_incidents_count)
        ongoingIncidentsCount = view.findViewById(R.id.ongoing_incidents_count)
        resolvedIncidentsCount = view.findViewById(R.id.resolved_incidents_count)

        filterButton.setOnClickListener {
            showFilterDialog()
        }
        exportButton.setOnClickListener {
            showExportOptionsDialog()
        }
    }

    private fun setupRecyclerView() {
        incidentAdapter = IncidentCardAdapter(
            context = requireContext(),
            incidents = emptyList(),
            onMapClick = { incident ->
                // Navigate to map home, and include locationID for active assistance statuses.
                val lat = incident.coordinates.split(",").getOrNull(0)?.trim()?.toDoubleOrNull() ?: 0.0
                val lng = incident.coordinates.split(",").getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0

                viewLifecycleOwner.lifecycleScope.launch {
                    val normalizedStatus = incident.status.trim().lowercase(Locale.getDefault())
                    val shouldOpenAssistanceModal = normalizedStatus in setOf("pending", "ongoing", "in_progress", "responding")

                    val locationID = if (shouldOpenAssistanceModal) {
                        withContext(Dispatchers.IO) { MySQLHelper.getLocationIDByAlertID(incident.alertId) }
                    } else {
                        null
                    }

                    val mapFragment = MapHomeFragment.newInstance(
                        locationID = locationID,
                        latitude = lat,
                        longitude = lng,
                        fullName = incident.userName,
                        floorLevel = incident.floorLevel,
                        status = incident.status
                    )
                    (activity as? SecurityOfficerActivity)?.navigateToMapHome(mapFragment)
                }
            },
            onResolveClick = { incident ->
                resolveIncident(incident)
            }
        )

        incidentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = incidentAdapter
        }
    }

    private fun showFilterDialog() {
        val dialog = Dialog(requireContext())
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
            "Pending" -> selectStatusChip(chipPending)
            "Ongoing" -> selectStatusChip(chipOngoing)
            "Resolved" -> selectStatusChip(chipResolved)
            "False Alarm" -> selectStatusChip(chipFalseAlarm)
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
            DatePickerDialog(requireContext(), { _, year, month, day ->
                val startCal = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                DatePickerDialog(requireContext(), { _, endYear, endMonth, endDay ->
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
        chipPending.setOnClickListener { tempSelectedStatus = "Pending"; selectStatusChip(chipPending) }
        chipOngoing.setOnClickListener { tempSelectedStatus = "Ongoing"; selectStatusChip(chipOngoing) }
        chipResolved.setOnClickListener { tempSelectedStatus = "Resolved"; selectStatusChip(chipResolved) }
        chipFalseAlarm.setOnClickListener { tempSelectedStatus = "False Alarm"; selectStatusChip(chipFalseAlarm) }

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
        viewModel.incidentData.observe(viewLifecycleOwner) { data ->
            loadingProgress.visibility = View.GONE
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

    // Logic Methods (Converted from Activity)
    private fun convertToIncidentData(rawData: List<List<String>>): List<IncidentCardAdapter.IncidentData> {
        return rawData.mapNotNull { row ->
            if (row.size >= 15) {
                IncidentCardAdapter.IncidentData(
                    alertId = row[0], userId = row[1], deviceId = row[2],
                    userName = row[3], coordinates = row[4], floorLevel = row[5],
                    status = row[6], timeOfAlert = row[7],
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
                    alertId = row[0], userId = row[1], deviceId = row[2],
                    userName = row[3], coordinates = row[4], floorLevel = row[5],
                    status = row[6], timeOfAlert = row[7],
                    resolvedOn = row[8].takeIf { it.isNotBlank() },
                    officerName = row[9].takeIf { it.isNotBlank() },
                    incidentDescription = row[10]
                )
            } else null
        }
    }

    private fun filterIncidents() {
        filteredIncidents = allIncidents.filter { incident ->
            // 1. Check Status
            val matchesStatus = when (selectedStatusFilter) {
                "All Status" -> true
                "Pending" -> incident.status.equals("pending", ignoreCase = true)
                "Ongoing" -> incident.status.equals("ongoing", ignoreCase = true)
                "Resolved" -> incident.status.equals("resolved", ignoreCase = true)
                "False Alarm" -> incident.status.equals("false alarm", ignoreCase = true)
                else -> true
            }

            // 2. Check Date
            val matchesDate = when (selectedDateFilter) {
                "All Time" -> true
                "Today" -> isToday(incident.timeOfAlert)
                "This Week" -> isThisWeek(incident.timeOfAlert)
                "This Month" -> isThisMonth(incident.timeOfAlert)
                "This Year" -> isThisYear(incident.timeOfAlert)
                "Custom Range" -> isInCustomRange(incident.timeOfAlert)
                else -> true
            }

            // 3. Check Relocation Location
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
        noIncidentsText.visibility = if (filteredIncidents.isEmpty()) View.VISIBLE else View.GONE
        incidentAdapter.updateIncidents(filteredIncidents)
    }

    private fun updateSummaryCards() {
        val pendingCount = allIncidents.count { it.status.equals("pending", ignoreCase = true) }
        totalIncidentsCount.text = pendingCount.toString()
        ongoingIncidentsCount.text = allIncidents.count { it.status.equals("ongoing", ignoreCase = true) }.toString()
        resolvedIncidentsCount.text = allIncidents.count { it.status.equals("resolved", ignoreCase = true) }.toString()
    }

    private fun resolveIncident(incident: IncidentCardAdapter.IncidentData) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "Resolving ${incident.alertId}", Toast.LENGTH_SHORT).show()
            loadIncidents()
        }
    }

    private fun showExportOptionsDialog() {
        val options = arrayOf("Excel (.xlsx)", "PDF (.pdf)", "Email / Share (Excel + PDF)")
        AlertDialog.Builder(requireContext())
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
                val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", MODE_PRIVATE)
                val officerName = sharedPreferences.getString("fullName", "Officer") ?: "Officer"
                val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val randomSuffix = String.format("%04d", java.util.Random().nextInt(10000))
                val excelFile = java.io.File(requireContext().cacheDir, "assistance_data_${dateStamp}_${randomSuffix}.xlsx")
                val pdfFile = java.io.File(requireContext().cacheDir, "assistance_data_${dateStamp}_${randomSuffix}.pdf")

                excelFile.outputStream().use { out ->
                    ExcelExportUtils.exportIncidentDataToExcelStream(out, dataToExport, officerName)
                }

                pdfFile.outputStream().use { out ->
                    PdfExportUtils.exportIncidentDataToPdfStream(out, dataToExport, officerName)
                }

                withContext(Dispatchers.Main) {
                    try {
                        val excelUri = androidx.core.content.FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            excelFile
                        )
                        val pdfUri = androidx.core.content.FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
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
                        Toast.makeText(requireContext(), "Failed to open email app: ${ex.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to prepare files: ${e.message}", Toast.LENGTH_LONG).show()
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
                incident.alertId, incident.userId, incident.deviceId, incident.userName,
                incident.coordinates, incident.floorLevel, incident.status,
                getAssistanceTypeForExport(incident), incident.timeOfAlert,
                incident.resolvedOn ?: "", incident.officerName ?: "",
                incident.actionFA, incident.actionINFO, incident.relocatedLocation
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
            this.type = mimeType
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

    // Helper functions (isToday, isInCustomRange, etc.) remain the same as your Activity code
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
        } catch (e: Exception) { false }
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

            val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", MODE_PRIVATE)
            val officerName = sharedPreferences.getString("fullName", "Officer") ?: "Officer"

            if (filteredIncidents.isEmpty()) {
                Toast.makeText(requireContext(), "Nothing to export (List is empty)", Toast.LENGTH_SHORT).show()
                return
            }

            if (uri != null) {
                try {
                    when (selectedExportFormat) {
                        "pdf" -> {
                            PdfExportUtils.exportIncidentDataToPdf(requireContext(), uri, dataToExport, officerName)
                            Toast.makeText(requireContext(), "PDF exported successfully", Toast.LENGTH_LONG).show()
                        }
                        "excel" -> {
                            ExcelExportUtils.exportIncidentDataToExcel(requireContext(), uri, dataToExport, officerName)
                            Toast.makeText(requireContext(), "Excel exported successfully", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Toast.makeText(requireContext(), "Unknown export format", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Export failed: No file selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadIncidents()
    }
}