package com.capstone.navicamp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.Button
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

class IncidentLog : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var tableLayout: TableLayout
    private lateinit var filterTypeSpinner: Spinner
    private lateinit var selectDateButton: Button

    private val viewModel: IncidentLogViewModel by viewModels()
    private lateinit var dataChangeReceiver: DataChangeReceiver
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 1000L // 1 second

    private val refreshRunnable = object : Runnable {
        override fun run() {
            viewModel.fetchIncidentData()
            handler.postDelayed(this, refreshInterval)
        }
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

        // Set up NavigationView item click listener
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
                    val intent = Intent(this, OfficerAccountSettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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

        // Initialize the TableLayout
        tableLayout = findViewById(R.id.tableLayout)  // Make sure this ID exists in your layout

        // Observe incident data from ViewModel
        viewModel.incidentData.observe(this, Observer { data ->
            val sortedData = data.sortedBy { it[0].toIntOrNull() ?: 0 }
            displayDataInTable(sortedData)
        })

        // Start refreshing incident data every second
        handler.postDelayed(refreshRunnable, refreshInterval)

        filterTypeSpinner = findViewById(R.id.filter_type_spinner)
        selectDateButton = findViewById(R.id.select_date_button)

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
        // Clear existing rows before adding new ones
        tableLayout.removeAllViews()

        // Add table header row
        val headerRow = TableRow(this)
        val headerText = arrayOf(
            "Alert ID", "Name", "User Type", "Device ID", "Coordinates", "Floor Level", "Reported Incident Type", "Status", "Time of Alert", "Resolved On"
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

        // Add rows for each incident
        incidentData.forEach { row ->
            val tableRow = TableRow(this)

            // Map each column correctly
            val cellData = listOf(
                row[0], // alertID
                row[1], // fullName
                row[2], // userType
                row[3], // deviceID
                "${row[4]}, ${row[5]}", // Combine latitude and longitude as "Coordinates"
                row[6], // floorLevel
                row[7], // alertType
                row[8], // status
                row[9], // alertDateTime
                row[10] // resolvedOn
            )

            cellData.forEach { cell ->
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
            "Year" -> data.filter { it[9].startsWith("${calendar.get(Calendar.YEAR)}") }
            "Month" -> data.filter { it[9].substring(0, 7) == "${calendar.get(Calendar.YEAR)}-${String.format("%02d", calendar.get(Calendar.MONTH) + 1)}" }
            "Week" -> data.filter {
                val incidentDate = dateFormat.parse(it[9]) ?: return@filter false
                val incidentCalendar = Calendar.getInstance()
                incidentCalendar.time = incidentDate
                calendar.get(Calendar.WEEK_OF_YEAR) == incidentCalendar.get(Calendar.WEEK_OF_YEAR) &&
                        calendar.get(Calendar.YEAR) == incidentCalendar.get(Calendar.YEAR)
            }
            "Day" -> data.filter {
                val incidentDate = dateFormat.parse(it[9]) ?: return@filter false
                val incidentCalendar = Calendar.getInstance()
                incidentCalendar.time = incidentDate
                calendar.get(Calendar.DAY_OF_YEAR) == incidentCalendar.get(Calendar.DAY_OF_YEAR) &&
                        calendar.get(Calendar.YEAR) == incidentCalendar.get(Calendar.YEAR)
            }
            else -> data
        }
    }
}