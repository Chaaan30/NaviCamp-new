package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var map: GoogleMap
    private lateinit var fab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Map"

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

        // Retrieve and display the officer's name
        val officerName = intent.getStringExtra("OFFICER_NAME")
        val headerView = navigationView.getHeaderView(0)
        val navNameHeader = headerView.findViewById<TextView>(R.id.nav_name_header)
        navNameHeader.text = officerName

        // Initialize the map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize FAB
        fab = findViewById(R.id.fab)
        fab.setOnClickListener {
            showBottomSheet()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Retrieve latitude and longitude from the Intent
        val latitude = intent.getDoubleExtra("LATITUDE", -34.0)
        val longitude = intent.getDoubleExtra("LONGITUDE", 151.0)

        // Add a marker and move the camera to the specified location
        val location = LatLng(latitude, longitude)
        map.addMarker(MarkerOptions().position(location).title("Assistance Location"))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 17f))

        // Show the bottom sheet
        showBottomSheet()
    }

    private fun showBottomSheet() {
        val floorLevel = intent.getStringExtra("FLOOR_LEVEL")
        val userID = intent.getStringExtra("USER_ID")
        val fullName = intent.getStringExtra("FULL_NAME")
        val dateTime = intent.getStringExtra("DATE_TIME")
        val status = intent.getStringExtra("STATUS")

        val bottomSheet = AssistanceBottomSheet.newInstance(
            floorLevel ?: "",
            userID ?: "",
            fullName ?: "",
            formatDateTime(dateTime ?: ""),
            status ?: ""
        )
        bottomSheet.show(supportFragmentManager, bottomSheet.tag)
    }

    private fun formatDateTime(dateTime: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault())
        val date = inputFormat.parse(dateTime)
        return outputFormat.format(date)
    }
}