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
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import java.text.SimpleDateFormat
import java.util.*

class DisplayRegisteredUsersActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var userTypeSpinner: Spinner
    private lateinit var creationDateSpinner: Spinner
    private lateinit var loadingProgress: ProgressBar
    private lateinit var tableLayout: TableLayout
    private var selectedUserType: String = "All"
    private var selectedCreationDateType: String = "All"
    private var selectedDate: Date = Date()
    private val viewModel: DisplayRegisteredUsersViewModel by viewModels()

    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 1000L // 1 second

    private val refreshRunnable = object : Runnable {
        override fun run() {
            viewModel.fetchUsers(selectedUserType, selectedCreationDateType, selectedDate)
            handler.postDelayed(this, refreshInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_registered_users)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Registered Users"

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

        val headerView = navigationView.getHeaderView(0)
        val navNameHeader: TextView = headerView.findViewById(R.id.nav_name_header)
        navNameHeader.text = UserSingleton.fullName

        tableLayout = findViewById(R.id.tableLayout)
        loadingProgress = findViewById(R.id.loading_progress)

        // Observe user data and update table with filtered data
        viewModel.userData.observe(this) { data ->
            android.util.Log.d("DisplayRegisteredUsers", "Fetched Registered Users data: $data")
            loadingProgress.visibility = View.GONE
            val filteredData = filterDataByDate(data, selectedCreationDateType, selectedDate)
            displayDataInTable(filteredData)
        }

        loadingProgress.visibility = View.VISIBLE
        handler.postDelayed(refreshRunnable, refreshInterval)

        userTypeSpinner = findViewById(R.id.user_type_spinner)
        creationDateSpinner = findViewById(R.id.creation_date_spinner)

        userTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedUserType = parent.getItemAtPosition(position).toString()
                (view as? TextView)?.text = "User Type: $selectedUserType"
                viewModel.fetchUsers(selectedUserType, selectedCreationDateType, selectedDate)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        creationDateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCreationDateType = parent.getItemAtPosition(position).toString()
                (view as? TextView)?.text = "Date: $selectedCreationDateType"

                if (selectedCreationDateType == "All") {
                    selectedDate = Date()
                    viewModel.userData.value?.let {
                        displayDataInTable(filterDataByDate(it, selectedCreationDateType, selectedDate))
                    }
                } else {
                    showFilterPicker()
                }
                viewModel.fetchUsers(selectedUserType, selectedCreationDateType, selectedDate)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.registered_user_types,
            R.layout.spinner_selected_item // for selected item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            userTypeSpinner.adapter = adapter
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.date_types,
            R.layout.spinner_selected_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            creationDateSpinner.adapter = adapter
        }
    }

    private fun displayDataInTable(users: List<UserData>) {
        // Remove all rows except header
        while (tableLayout.childCount > 1) {
            tableLayout.removeViewAt(1)
        }
        for (user in users) {
            val row = TableRow(this)
            row.addView(createCell(user.userID))
            row.addView(createCell(user.fullName))
            row.addView(createCell(user.email))
            row.addView(createCell(user.contactNumber))
            row.addView(createCell(user.userType))
            row.addView(createCell(user.createdOn))
            tableLayout.addView(row)
        }
    }

    private fun createCell(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setPadding(20, 20, 20, 20)
        return tv
    }

    private fun showFilterPicker() {
        when (selectedCreationDateType) {
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
            viewModel.userData.value?.let {
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
            viewModel.userData.value?.let {
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
            viewModel.userData.value?.let {
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
            viewModel.userData.value?.let {
                displayDataInTable(filterDataByDate(it, "Day", selectedDate))
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun filterDataByDate(
        data: List<UserData>,
        filterType: String,
        selectedDate: Date = Date()
    ): List<UserData> {
        // Try both formats
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
                val date = parseDate(it.createdOn)
                date != null && calendar.get(Calendar.YEAR) == Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
            }
            "Month" -> data.filter {
                val date = parseDate(it.createdOn)
                date != null &&
                        calendar.get(Calendar.YEAR) == Calendar.getInstance().apply { time = date }.get(Calendar.YEAR) &&
                        calendar.get(Calendar.MONTH) == Calendar.getInstance().apply { time = date }.get(Calendar.MONTH)
            }
            "Week" -> data.filter {
                val date = parseDate(it.createdOn)
                if (date == null) return@filter false
                val userCal = Calendar.getInstance().apply { time = date }
                calendar.get(Calendar.YEAR) == userCal.get(Calendar.YEAR) &&
                        calendar.get(Calendar.WEEK_OF_YEAR) == userCal.get(Calendar.WEEK_OF_YEAR)
            }
            "Day" -> data.filter {
                val date = parseDate(it.createdOn)
                if (date == null) return@filter false
                val userCal = Calendar.getInstance().apply { time = date }
                calendar.get(Calendar.YEAR) == userCal.get(Calendar.YEAR) &&
                        calendar.get(Calendar.DAY_OF_YEAR) == userCal.get(Calendar.DAY_OF_YEAR)
            }
            else -> data
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
    }
}