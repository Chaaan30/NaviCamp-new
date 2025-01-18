package com.capstone.navicamp

import android.os.Bundle
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

class SecurityOfficerActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var registeredLDUserCount: TextView
    private lateinit var iotWheelchairCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_officer)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
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
        }
    }
}
