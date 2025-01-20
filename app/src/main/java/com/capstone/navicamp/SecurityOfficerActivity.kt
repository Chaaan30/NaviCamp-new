package com.capstone.navicamp

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.activity.viewModels
import androidx.lifecycle.Observer


class SecurityOfficerActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var registeredLDUserCount: TextView
    private lateinit var iotWheelchairCount: TextView
    private lateinit var navigationView: NavigationView
    private lateinit var assistanceLayout: LinearLayout

    private val viewModel: SecurityOfficerViewModel by viewModels()
    private lateinit var dataChangeReceiver: DataChangeReceiver
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 1000L // 5 seconds

    private val refreshRunnable = object : Runnable {
        override fun run() {
            viewModel.fetchPendingItems()
            handler.postDelayed(this, refreshInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_officer)

        // Initialize navigationView
        navigationView = findViewById(R.id.navigation_view)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Dashboard"

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

        // Initialize the TextView
        registeredLDUserCount = findViewById(R.id.registeredLDUserCount)
        iotWheelchairCount = findViewById(R.id.iotWheelchairCount)

        // Fetch user count and update UI
        fetchAndDisplayUserCount()
        fetchAndDisplayIoTWheelchairCount()
    }

    private fun fetchAndDisplayUserCount() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userCount = MySQLHelper.getUserCount() // Fetch user count from database
                withContext(Dispatchers.Main) {
                    registeredLDUserCount.text = String.format(Locale.getDefault(), "%d", userCount)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    registeredLDUserCount.text = "0"
                }
            }
        }
    }

    private fun fetchAndDisplayIoTWheelchairCount() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wheelchairCount = MySQLHelper.getIoTWheelchairCount() // Fetch user count from database
                withContext(Dispatchers.Main) {
                    iotWheelchairCount.text = String.format(Locale.getDefault(), "%d", wheelchairCount)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    iotWheelchairCount.text = "0"
                }
            }

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

        assistanceLayout = findViewById(R.id.assistance_layout)

        // Observe the LiveData from the ViewModel
        viewModel.pendingItems.observe(this, Observer { pendingItems ->
            updateAssistanceCards(pendingItems)
        })

        // Fetch the initial data
        viewModel.fetchPendingItems()

        // Register the BroadcastReceiver
        dataChangeReceiver = DataChangeReceiver {
            viewModel.fetchPendingItems()
        }
        val intentFilter = IntentFilter("com.capstone.navicamp.DATA_CHANGED")
        registerReceiver(dataChangeReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        // Update secoff_fullname
        findViewById<TextView>(R.id.secoff_fullname)?.text = UserSingleton.fullName

        // Update nav_name_header in NavigationView
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = UserSingleton.fullName
        }

        // Fetch pending items and update UI
        viewModel.fetchPendingItems()

        // Start the refresh timer
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        // Stop the refresh timer
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dataChangeReceiver)
    }

    private fun updateAssistanceCards(pendingItems: List<LocationItem>) {
        assistanceLayout.removeAllViews()
        for (item in pendingItems) {
            val cardView = LayoutInflater.from(this).inflate(R.layout.assistance_card, assistanceLayout, false)
            val fullNameTextView = cardView.findViewById<TextView>(R.id.full_name_text)
            val floorLevelTextView = cardView.findViewById<TextView>(R.id.floor_level_text)
            val respondButton = cardView.findViewById<Button>(R.id.respond_button)

            fullNameTextView.text = item.fullName
            floorLevelTextView.text = item.floorLevel
            respondButton.setOnClickListener {
                // Handle respond button click
            }

            assistanceLayout.addView(cardView)
        }
    }
}
