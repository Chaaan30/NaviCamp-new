package com.capstone.navicamp

import android.app.Activity.RESULT_OK
import android.app.DatePickerDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Build
import android.os.Bundle
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

class OfficerIncidentsFragment : Fragment(R.layout.fragment_officer_incidents) {

    // UI Components
    private lateinit var incidentsRecyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var noIncidentsText: TextView
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
    private var customStartDate: Calendar? = null
    private var customEndDate: Calendar? = null
    private var selectedExportFormat = "csv"

    companion object {
        private const val CREATE_FILE_REQUEST_CODE = 1001
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupRecyclerView()
        setupFilters(view)
        setupObservers()
        loadIncidents()
    }

    private fun setupViews(view: View) {
        incidentsRecyclerView = view.findViewById(R.id.incidents_recycler_view)
        loadingProgress = view.findViewById(R.id.loading_progress)
        noIncidentsText = view.findViewById(R.id.no_incidents_text)
        exportButton = view.findViewById(R.id.export_button)
        totalIncidentsCount = view.findViewById(R.id.total_incidents_count)
        ongoingIncidentsCount = view.findViewById(R.id.ongoing_incidents_count)
        resolvedIncidentsCount = view.findViewById(R.id.resolved_incidents_count)

        exportButton.setOnClickListener {
            showExportOptionsDialog()
        }
    }

    private fun setupRecyclerView() {
        incidentAdapter = IncidentCardAdapter(
            context = requireContext(),
            incidents = emptyList(),
            onMapClick = { incident ->
                // Navigate to map home fragment with coordinates
                val lat = incident.coordinates.split(",")[0].trim().toDoubleOrNull() ?: 0.0
                val lng = incident.coordinates.split(",")[1].trim().toDoubleOrNull() ?: 0.0
                val mapFragment = MapHomeFragment.newInstance(
                    latitude = lat,
                    longitude = lng,
                    fullName = incident.userName
                )
                (activity as? SecurityOfficerActivity)?.navigateToMapHome(mapFragment)
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

    private fun setupFilters(view: View) {
        // Date Filters
        view.findViewById<Chip>(R.id.chip_all_time).setOnClickListener { selectDateFilter(view, "All Time") }
        view.findViewById<Chip>(R.id.chip_today).setOnClickListener { selectDateFilter(view, "Today") }
        view.findViewById<Chip>(R.id.chip_this_week).setOnClickListener { selectDateFilter(view, "This Week") }
        view.findViewById<Chip>(R.id.chip_this_month).setOnClickListener { selectDateFilter(view, "This Month") }
        view.findViewById<Chip>(R.id.chip_this_year).setOnClickListener { selectDateFilter(view, "This Year") }
        view.findViewById<Chip>(R.id.chip_custom_date).setOnClickListener { showCustomDateRangePicker(view) }

        // Status Filters
        view.findViewById<Chip>(R.id.chip_all_status).setOnClickListener { selectStatusFilter(view, "All Status") }
        view.findViewById<Chip>(R.id.chip_pending).setOnClickListener { selectStatusFilter(view, "Pending") }
        view.findViewById<Chip>(R.id.chip_ongoing).setOnClickListener { selectStatusFilter(view, "Ongoing") }
        view.findViewById<Chip>(R.id.chip_resolved).setOnClickListener { selectStatusFilter(view, "Resolved") }
        view.findViewById<Chip>(R.id.chip_false_alarm).setOnClickListener { selectStatusFilter(view, "False Alarm") }
    }

    private fun selectDateFilter(view: View, filter: String) {
        val ids = listOf(R.id.chip_all_time, R.id.chip_today, R.id.chip_this_week, R.id.chip_this_month, R.id.chip_this_year, R.id.chip_custom_date)
        ids.forEach { view.findViewById<Chip>(it).isChecked = false }

        when (filter) {
            "All Time" -> view.findViewById<Chip>(R.id.chip_all_time).isChecked = true
            "Today" -> view.findViewById<Chip>(R.id.chip_today).isChecked = true
            "This Week" -> view.findViewById<Chip>(R.id.chip_this_week).isChecked = true
            "This Month" -> view.findViewById<Chip>(R.id.chip_this_month).isChecked = true
            "This Year" -> view.findViewById<Chip>(R.id.chip_this_year).isChecked = true
            "Custom Range" -> view.findViewById<Chip>(R.id.chip_custom_date).isChecked = true
        }
        selectedDateFilter = filter
        filterIncidents()
    }

    private fun selectStatusFilter(view: View, filter: String) {
        val ids = listOf(
            R.id.chip_all_status,
            R.id.chip_pending,
            R.id.chip_ongoing,
            R.id.chip_resolved,
            R.id.chip_false_alarm
        )
        ids.forEach { view.findViewById<Chip>(it).isChecked = false }

        when (filter) {
            "All Status" -> view.findViewById<Chip>(R.id.chip_all_status).isChecked = true
            "Pending" -> view.findViewById<Chip>(R.id.chip_pending).isChecked = true
            "Ongoing" -> view.findViewById<Chip>(R.id.chip_ongoing).isChecked = true
            "Resolved" -> view.findViewById<Chip>(R.id.chip_resolved).isChecked = true
            "False Alarm" -> view.findViewById<Chip>(R.id.chip_false_alarm).isChecked = true
        }
        selectedStatusFilter = filter
        filterIncidents()
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
            if (row.size >= 11) {
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

            // 2. Check Date (Handled via helper functions)
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
        val options = arrayOf("CSV", "Excel")
        AlertDialog.Builder(requireContext())
            .setTitle("Export Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportIncidents("csv")
                    1 -> exportIncidents("excel")
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

        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        // Store the format for use in onActivityResult
        selectedExportFormat = format

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): Use SAF
            val (mimeType, extension) = when (format) {
                "csv" -> Pair("text/csv", "csv")
                "excel" -> Pair("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
                else -> Pair("text/csv", "csv")
            }

            val createFileIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                this.type = mimeType
                putExtra(Intent.EXTRA_TITLE, "assistance_data_${dateStamp}.${extension}")
            }
            startActivityForResult(createFileIntent,
                OfficerIncidentsFragment.Companion.CREATE_FILE_REQUEST_CODE
            )
        } else {
            // Android 10 and below
            when (format) {
                "csv" -> {
                    val file = exportIncidentDataToCSV(requireContext(), dataToExport)
                    if (file != null) {
                        Toast.makeText(requireContext(), "CSV exported successfully to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "CSV export failed", Toast.LENGTH_SHORT).show()
                    }
                }
                "excel" -> {
                    Toast.makeText(requireContext(), "${format.uppercase()} export not implemented for Android 10 and below", Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun showCustomDateRangePicker(view: View) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            customStartDate = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
            DatePickerDialog(requireContext(), { _, eYear, eMonth, eDay ->
                customEndDate = Calendar.getInstance().apply { set(eYear, eMonth, eDay, 23, 59, 59) }
                view.findViewById<Chip>(R.id.chip_custom_date).text = "Selected Range"
                selectDateFilter(view, "Custom Range")
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OfficerIncidentsFragment.Companion.CREATE_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
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

            val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", MODE_PRIVATE)
            val officerName = sharedPreferences.getString("fullName", "Officer") ?: "Officer"

            if (filteredIncidents.isEmpty()) {
                Toast.makeText(requireContext(), "Nothing to export (List is empty)", Toast.LENGTH_SHORT).show()
                return
            }

            if (uri != null) {
                try {
                    when (selectedExportFormat) {
                        "csv" -> {
                            CsvExportUtils.exportIncidentDataToCsv(requireContext(), uri, dataToExport, officerName)
                            Toast.makeText(requireContext(), "CSV exported successfully", Toast.LENGTH_LONG).show()
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