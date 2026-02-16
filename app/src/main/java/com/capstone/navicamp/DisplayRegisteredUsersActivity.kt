package com.capstone.navicamp

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.*

class DisplayRegisteredUsersActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var userTypeSpinner: Spinner
    private lateinit var searchEditText: EditText
    private lateinit var loadingProgress: ProgressBar
    private lateinit var usersLayout: LinearLayout
    private val viewModel: DisplayRegisteredUsersViewModel by viewModels()
    
    private var allUsers = listOf<UserData>()
    private var filteredUsers = listOf<UserData>()
    private var pollingJob: Job? = null
    private val POLLING_INTERVAL = 15000L // 15 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_registered_users)

        setupViews()
        setupSidebar()
        setupFilters()
        loadUsers()
    }

    private fun setupViews() {
        usersLayout = findViewById(R.id.users_layout)
        userTypeSpinner = findViewById(R.id.user_type_spinner)
        searchEditText = findViewById(R.id.search_edit_text)
        loadingProgress = findViewById(R.id.loading_progress)
        
        // Hide refresh button if it exists
        findViewById<Button>(R.id.refresh_button)?.visibility = View.GONE
        
        // Start smart polling
        startSmartPolling()
    }

    private fun setupSidebar() {
        navigationView = findViewById(R.id.navigation_view)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Registered Users"

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
                    val intent = Intent(this, OfficerAccountSettingsActivity::class.java)
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
        val filterOptions = arrayOf("All", "Temporarily Disabled", "Permanently Disabled")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userTypeSpinner.adapter = adapter

        userTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                filterUsers()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterUsers()
            }
        })
    }

    private fun loadUsers() {
        findViewById<ProgressBar>(R.id.loading_progress).visibility = View.VISIBLE
        findViewById<TextView>(R.id.no_users_text).visibility = View.GONE
        
        lifecycleScope.launch {
            allUsers = withContext(Dispatchers.IO) {
                MySQLHelper.getAllVerifiedUsers()
            }
            filteredUsers = allUsers
            updateUserCards()
            findViewById<ProgressBar>(R.id.loading_progress).visibility = View.GONE
        }
    }

    private fun filterUsers() {
        val selectedFilter = userTypeSpinner.selectedItem.toString()
        val searchQuery = searchEditText.text.toString().lowercase().trim()

        filteredUsers = allUsers.filter { user ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                else -> user.userType.equals(selectedFilter, ignoreCase = true)
            }

            val matchesSearch = if (searchQuery.isEmpty()) {
                true
            } else {
                user.fullName.lowercase().contains(searchQuery) ||
                user.userID.lowercase().contains(searchQuery) ||
                user.email.lowercase().contains(searchQuery) ||
                user.contactNumber.lowercase().contains(searchQuery)
            }

            matchesFilter && matchesSearch
        }

        updateUserCards()
    }

    private fun updateUserCards() {
        usersLayout.removeAllViews()
        
        val noUsersText = findViewById<TextView>(R.id.no_users_text)
        val userCount = findViewById<TextView>(R.id.user_count)
        
        userCount.text = "Total Users: ${filteredUsers.size}"

        if (filteredUsers.isEmpty()) {
            noUsersText.visibility = View.VISIBLE
            noUsersText.text = if (allUsers.isEmpty()) {
                "No registered users found"
            } else {
                "No users match the current filter"
            }
        } else {
            noUsersText.visibility = View.GONE

            for (user in filteredUsers) {
                val cardView = layoutInflater.inflate(R.layout.user_card, usersLayout, false)
                setupUserCard(cardView, user)
                usersLayout.addView(cardView)
            }
        }
    }

    private fun setupUserCard(cardView: View, user: UserData) {
        val userIdText = cardView.findViewById<TextView>(R.id.user_id_text)
        val userNameText = cardView.findViewById<TextView>(R.id.user_name_text)
        val userTypeBadge = cardView.findViewById<TextView>(R.id.user_type_badge)
        val emailText = cardView.findViewById<TextView>(R.id.email_text)
        val contactText = cardView.findViewById<TextView>(R.id.contact_text)
        val createdDateText = cardView.findViewById<TextView>(R.id.created_date_text)
        val userTypeIndicator = cardView.findViewById<View>(R.id.user_type_indicator)

        userIdText.text = "ID: ${user.userID}"
        userNameText.text = user.fullName
        userTypeBadge.text = user.userType
        emailText.text = "Email: ${user.email}"
        contactText.text = "Contact: ${user.contactNumber}"
        createdDateText.text = "Registered: ${user.createdOn}"

        // Set user type badge and indicator colors based on type
        when (user.userType.lowercase()) {
            "temporarily disabled" -> {
                userTypeBadge.backgroundTintList = ColorStateList.valueOf(resources.getColor(android.R.color.holo_blue_dark))
                userTypeIndicator.setBackgroundColor(resources.getColor(android.R.color.holo_blue_dark))
            }
            "permanently disabled" -> {
                userTypeBadge.backgroundTintList = ColorStateList.valueOf(resources.getColor(android.R.color.holo_green_dark))
                userTypeIndicator.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark))
            }
            else -> {
                userTypeBadge.backgroundTintList = ColorStateList.valueOf(resources.getColor(android.R.color.darker_gray))
                userTypeIndicator.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
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
                    val newUsers = withContext(Dispatchers.IO) {
                        MySQLHelper.getAllVerifiedUsers()
                    }
                    
                    // Only update if data has changed
                    if (newUsers != allUsers) {
                        allUsers = newUsers
                        filterUsers()
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