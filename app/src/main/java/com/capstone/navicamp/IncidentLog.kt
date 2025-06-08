package com.capstone.navicamp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
    private lateinit var loadingProgress: ProgressBar
    private lateinit var exportButton: Button
    private lateinit var dateFilterSpinner: Spinner
    private var selectedFilterType: String = "All"
    private var selectedDate: Date = Date()

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

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    // Clear SharedPreferences
                    val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.clear()
                    editor.apply()

                    // Navigate to MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }

                R.id.nav_item1 -> {
                    // Navigate to OfficerAccountSettingsActivity
                    val intent = Intent(this, OfficerAccountSettingsActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.nav_item2 -> {
                    // Navigate to SecurityOfficerActivity and clear the activity stack
                    val intent = Intent(this, SecurityOfficerActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    true
                }

                else -> false
            }
        }

        // Set the officer's full name in the navigation header
        val headerView = navigationView.getHeaderView(0)
        val navNameHeader: TextView = headerView.findViewById(R.id.nav_name_header)
        navNameHeader.text = UserSingleton.fullName

        // Initialize the TableLayout and ProgressBar
        tableLayout = findViewById(R.id.tableLayout)
        loadingProgress = findViewById(R.id.loading_progress)

        // Show ProgressBar and start refreshing incident data every second
        loadingProgress.visibility = View.VISIBLE
        handler.postDelayed(refreshRunnable, refreshInterval)

        dateFilterSpinner = findViewById(R.id.date_filter_spinner)

        dateFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedFilterType = parent.getItemAtPosition(position).toString()
                (view as? TextView)?.text = "Date: $selectedFilterType"

                if (selectedFilterType == "All") {
                    selectedDate = Date()
                    viewModel.incidentData.value?.let {
                        displayDataInTable(filterDataByDate(it, selectedFilterType, selectedDate))
                    }
                } else {
                    showFilterPicker()
                }
                viewModel.fetchIncidentData()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.date_types,
            R.layout.spinner_selected_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            dateFilterSpinner.adapter = adapter
        }

        // Observe incident data and filter
        viewModel.incidentData.observe(this) { data ->
            loadingProgress.visibility = View.GONE
            val filteredData = filterDataByDate(data, selectedFilterType, selectedDate)
            displayDataInTable(filteredData)
        }

        exportButton = findViewById(R.id.export_button)
        exportButton.setOnClickListener {
            val data = viewModel.incidentData.value ?: emptyList()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+): Use SAF
                val createFileIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "incident_data.csv")
                }
                startActivityForResult(createFileIntent, CREATE_FILE_REQUEST_CODE)
            } else {
                // Android 10 and below
                val file = exportIncidentDataToCSV(this, data)
                if (file != null) {
                    Toast.makeText(this, "Exported successfully to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFilterPicker() {
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
            val selected = Calendar.getInstance()
            selected.set(year, 0, 1)
            selectedDate = selected.time
            viewModel.incidentData.value?.let {
                displayDataInTable(filterDataByDate(it, "Year", selectedDate))
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showMonthPicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, _ ->
            val selected = Calendar.getInstance()
            selected.set(year, month, 1)
            selectedDate = selected.time
            viewModel.incidentData.value?.let {
                displayDataInTable(filterDataByDate(it, "Month", selectedDate))
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showWeekPicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selected = Calendar.getInstance()
            selected.set(year, month, dayOfMonth)
            selectedDate = selected.time
            viewModel.incidentData.value?.let {
                displayDataInTable(filterDataByDate(it, "Week", selectedDate))
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showDayPicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selected = Calendar.getInstance()
            selected.set(year, month, dayOfMonth)
            selectedDate = selected.time
            viewModel.incidentData.value?.let {
                displayDataInTable(filterDataByDate(it, "Day", selectedDate))
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun filterDataByDate(
        data: List<List<String>>,
        filterType: String,
        selectedDate: Date = Date()
    ): List<List<String>> {
        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate

        fun parseDate(dateStr: String): Date? {
            for (format in dateFormats) {
                try {
                    return format.parse(dateStr)
                } catch (_: Exception) {}
            }
            return null
        }

        return when (filterType) {
            "Year" -> data.filter {
                val date = parseDate(it[8]) // Assuming "Time of Alert" is at index 8
                date != null && calendar.get(Calendar.YEAR) == Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
            }
            "Month" -> data.filter {
                val date = parseDate(it[8]) // Assuming "Time of Alert" is at index 8
                date != null &&
                        calendar.get(Calendar.YEAR) == Calendar.getInstance().apply { time = date }.get(Calendar.YEAR) &&
                        calendar.get(Calendar.MONTH) == Calendar.getInstance().apply { time = date }.get(Calendar.MONTH)
            }
            "Week" -> data.filter {
                val date = parseDate(it[8]) // Assuming "Time of Alert" is at index 8
                if (date == null) return@filter false
                val userCal = Calendar.getInstance().apply { time = date }
                calendar.get(Calendar.YEAR) == userCal.get(Calendar.YEAR) &&
                        calendar.get(Calendar.WEEK_OF_YEAR) == userCal.get(Calendar.WEEK_OF_YEAR)
            }
            "Day" -> data.filter {
                val date = parseDate(it[8]) // Assuming "Time of Alert" is at index 8
                if (date == null) return@filter false
                val userCal = Calendar.getInstance().apply { time = date }
                calendar.get(Calendar.YEAR) == userCal.get(Calendar.YEAR) &&
                        calendar.get(Calendar.DAY_OF_YEAR) == userCal.get(Calendar.DAY_OF_YEAR)
            }
            else -> data
        }
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