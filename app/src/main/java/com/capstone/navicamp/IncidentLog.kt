package com.capstone.navicamp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.content.Context
import android.os.Build
import android.widget.Toast
import exportIncidentDataToCSV
import exportIncidentDataToCSVWithUri
import java.io.File
import java.io.FileWriter

class IncidentLog : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var tableLayout: TableLayout
    private lateinit var filterTypeSpinner: Spinner
    private lateinit var selectDateButton: Button
    private lateinit var loadingProgress: ProgressBar
    private lateinit var exportButton: Button

    private val viewModel: IncidentLogViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 1000L // 1 second

    private val refreshRunnable = object : Runnable {
        override fun run() {
            viewModel.fetchIncidentData()
            handler.postDelayed(this, refreshInterval)
        }
    }

    companion object {
        private const val CREATE_FILE_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incident_log)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Incident Log"

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Initialize navigationView
        navigationView = findViewById(R.id.navigation_view)

        // Set the officer's full name in the navigation header
        val headerView = navigationView.getHeaderView(0)
        val navNameHeader: TextView = headerView.findViewById(R.id.nav_name_header)
        navNameHeader.text = UserSingleton.fullName

        // Initialize the TableLayout and ProgressBar
        tableLayout = findViewById(R.id.tableLayout)
        loadingProgress = findViewById(R.id.loading_progress)

        // Observe incident data from ViewModel
        viewModel.incidentData.observe(this, Observer { data ->
            android.util.Log.d("IncidentLog", "Fetched incident data: $data")
            loadingProgress.visibility = View.GONE
            val sortedData = data.sortedBy { it[0].toIntOrNull() ?: 0 }
            displayDataInTable(sortedData)
        })

        // Show ProgressBar and start refreshing incident data every second
        loadingProgress.visibility = View.VISIBLE
        handler.postDelayed(refreshRunnable, refreshInterval)

        filterTypeSpinner = findViewById(R.id.filter_type_spinner)
        selectDateButton = findViewById(R.id.select_date_button)
        exportButton = findViewById(R.id.export_button)

        selectDateButton.setOnClickListener { showFilterPicker() }

        filterTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedFilter = parent?.getItemAtPosition(position).toString()
                viewModel.incidentData.observe(this@IncidentLog) { data ->
                    val filteredData = filterDataByDate(data, selectedFilter)
                    displayDataInTable(filteredData)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        exportButton.setOnClickListener {
            val data = viewModel.incidentData.value ?: emptyList()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+): Use SAF
                val createFileIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "incident_logs.csv")
                }
                startActivityForResult(createFileIntent, CREATE_FILE_REQUEST_CODE)
            } else {
                // Android 10 and below
                val file = exportIncidentDataToCSV(this, data)
                if (file != null) {
                    Toast.makeText(this, "Exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFilterPicker() {
        val selectedFilterType = filterTypeSpinner.selectedItem.toString()
        when (selectedFilterType) {
            "Year" -> showYearPicker()
            "Month" -> showMonthPicker()
            "Week" -> showWeekPicker()
            "Day" -> showDayPicker()
        }
    }

    private fun showYearPicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, _, _ ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, 0, 1)
            viewModel.incidentData.observe(this) { data ->
                val filteredData = filterDataByDate(data, "Year", selectedDate.time)
                displayDataInTable(filteredData)
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showMonthPicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, _ ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, 1)
            viewModel.incidentData.observe(this) { data ->
                val filteredData = filterDataByDate(data, "Month", selectedDate.time)
                displayDataInTable(filteredData)
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showWeekPicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, dayOfMonth)
            viewModel.incidentData.observe(this) { data ->
                val filteredData = filterDataByDate(data, "Week", selectedDate.time)
                displayDataInTable(filteredData)
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showDayPicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, dayOfMonth)
            viewModel.incidentData.observe(this) { data ->
                val filteredData = filterDataByDate(data, "Day", selectedDate.time)
                displayDataInTable(filteredData)
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun displayDataInTable(incidentData: List<List<String>>) {
        tableLayout.removeAllViews()

        // Header for 11 columns
        val headerRow = TableRow(this)
        val headerText = arrayOf(
            "Alert ID", "User ID", "Device ID", "Name", "Coordinates", "Floor Level",
            "Status", "Time of Alert", "Resolved On", "Officer Name", "Incident Description"
        )
        headerText.forEach { text ->
            val textView = TextView(this).apply {
                setText(text)
                setPadding(20, 20, 20, 20)
                setTextColor(resources.getColor(android.R.color.white))
                setBackgroundColor(resources.getColor(R.color.custom_blue))
            }
            headerRow.addView(textView)
        }
        tableLayout.addView(headerRow)

        // Add rows for each incident (expecting 11 columns)
        incidentData.forEach { row ->
            if (row.size < 11) {
                android.util.Log.w("IncidentLog", "Skipping row with insufficient columns: $row")
                return@forEach
            }
            val tableRow = TableRow(this)
            row.forEach { cell ->
                val textView = TextView(this).apply {
                    text = cell
                    setPadding(20, 20, 20, 20)
                    setTextColor(resources.getColor(android.R.color.black))
                }
                tableRow.addView(textView)
            }
            tableLayout.addView(tableRow)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable) // Stop the handler when the activity is destroyed
    }

    private fun filterDataByDate(data: List<List<String>>, filterType: String, selectedDate: Date = Date()): List<List<String>> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate

        return when (filterType) {
            "Year" -> data.filter { it[7].startsWith("${calendar.get(Calendar.YEAR)}") }
            "Month" -> data.filter { it[7].substring(0, 7) == "${calendar.get(Calendar.YEAR)}-${String.format("%02d", calendar.get(Calendar.MONTH) + 1)}" }
            "Week" -> {
                val weekStart = calendar.clone() as Calendar
                weekStart.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)

                val weekEnd = calendar.clone() as Calendar
                weekEnd.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                weekEnd.add(Calendar.DAY_OF_WEEK, 6)

                data.filter {
                    try {
                        val incidentDate = dateFormat.parse(it[7])
                        incidentDate.after(weekStart.time) && incidentDate.before(weekEnd.time)
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            "Day" -> data.filter {
                val incidentDate = dateFormat.parse(it[7]) ?: return@filter false
                val incidentCalendar = Calendar.getInstance()
                incidentCalendar.time = incidentDate
                calendar.get(Calendar.DAY_OF_YEAR) == incidentCalendar.get(Calendar.DAY_OF_YEAR) &&
                        calendar.get(Calendar.YEAR) == incidentCalendar.get(Calendar.YEAR)
            }
            else -> data
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            val incidentData = viewModel.incidentData.value ?: emptyList()
            if (uri != null) {
                exportIncidentDataToCSVWithUri(this, uri, incidentData)
                Toast.makeText(this, "Exported successfully", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}