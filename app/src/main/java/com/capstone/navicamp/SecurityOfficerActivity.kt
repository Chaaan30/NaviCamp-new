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
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import android.view.View


class SecurityOfficerActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var assistanceLayout: LinearLayout

    private val viewModel: SecurityOfficerViewModel by viewModels()
    private lateinit var dataChangeReceiver: DataChangeReceiver
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 1000L // 1 second

    private val refreshRunnable = object : Runnable {
        override fun run() {
            viewModel.fetchPendingItems()
            viewModel.fetchUserCount()
            viewModel.fetchDeviceCount()
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

        // Initialize assistanceLayout
        assistanceLayout = findViewById(R.id.assistance_layout)

        // Observe the LiveData from the ViewModel
        viewModel.pendingItems.observe(this, Observer { pendingItems ->
            updateAssistanceCards(pendingItems)
        })

        viewModel.userCount.observe(this, Observer { count ->
            findViewById<TextView>(R.id.registered_users)?.text = count.toString()
        })

        viewModel.deviceCount.observe(this, Observer { count ->
            findViewById<TextView>(R.id.iot_devices)?.text = count.toString()
        })

        // Fetch the initial data
        viewModel.fetchPendingItems()
        viewModel.fetchUserCount()
        viewModel.fetchDeviceCount()

        // Register the BroadcastReceiver
        dataChangeReceiver = DataChangeReceiver {
            viewModel.fetchPendingItems()
            viewModel.fetchUserCount()
            viewModel.fetchDeviceCount()
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
        viewModel.fetchUserCount()
        viewModel.fetchDeviceCount()

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
        val noAssistanceTextView = findViewById<TextView>(R.id.no_assistance_text)

        if (pendingItems.isEmpty()) {
            noAssistanceTextView.visibility = View.VISIBLE
        } else {
            noAssistanceTextView.visibility = View.GONE
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

            val sortedItems = pendingItems.sortedByDescending { item ->
                inputFormat.parse(item.dateTime)
            }

            for (item in sortedItems) {
                val cardView = LayoutInflater.from(this)
                    .inflate(R.layout.assistance_card, assistanceLayout, false)
                val fullNameTextView = cardView.findViewById<TextView>(R.id.full_name_text)
                val createdOnDateTextView =
                    cardView.findViewById<TextView>(R.id.created_on_date_text)
                val createdOnTimeTextView =
                    cardView.findViewById<TextView>(R.id.created_on_time_text)
                val floorLevelTextView = cardView.findViewById<TextView>(R.id.floor_level_text)
                val respondButton = cardView.findViewById<Button>(R.id.respond_button)

                fullNameTextView.text = item.fullName

                val date = inputFormat.parse(item.dateTime)
                val formattedDate = date?.let { dateFormat.format(it) } ?: item.dateTime
                val formattedTime = date?.let { timeFormat.format(it) } ?: item.dateTime
                createdOnDateTextView.text = formattedDate
                createdOnTimeTextView.text = formattedTime

                floorLevelTextView.text = item.floorLevel
                respondButton.setOnClickListener {
                    val locationID = item.locationID
                    val officerName = UserSingleton.fullName

                    lifecycleScope.launch(Dispatchers.IO) {
                        val locationItem = MySQLHelper.getLocationItemById(locationID)
                        withContext(Dispatchers.Main) {
                            val intent =
                                Intent(this@SecurityOfficerActivity, MapActivity::class.java)
                            intent.putExtra("OFFICER_NAME", officerName)
                            intent.putExtra("LATITUDE", locationItem.latitude)
                            intent.putExtra("LONGITUDE", locationItem.longitude)
                            intent.putExtra("FLOOR_LEVEL", locationItem.floorLevel)
                            intent.putExtra("LOCATION_ID", locationItem.locationID)
                            intent.putExtra("USER_ID", locationItem.userID)
                            intent.putExtra("FULL_NAME", locationItem.fullName)
                            intent.putExtra("DATE_TIME", locationItem.dateTime)
                            intent.putExtra("STATUS", locationItem.status)
                            startActivity(intent)
                        }
                    }
                }
                assistanceLayout.addView(cardView)
            }
        }
    }
}