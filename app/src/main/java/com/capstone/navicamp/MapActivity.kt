package com.capstone.navicamp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var map: GoogleMap
    private lateinit var fab: FloatingActionButton
    private var officerLocationCallback: LocationCallback? = null
    private var officerID: String? = null

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
                    val intent = Intent(this, OfficerAccountSettingsFragment::class.java)
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
        val locationID = intent.getStringExtra("LOCATION_ID")
        
        // Fetch the latest data from database to ensure accuracy
        CoroutineScope(Dispatchers.IO).launch {
            val latestLocationItem = MySQLHelper.getLocationItemById(locationID ?: "")
            
            withContext(Dispatchers.Main) {
                val modalDialog = AssistanceModalDialog.newInstance(
                    latestLocationItem.floorLevel,
                    latestLocationItem.locationID,
                    latestLocationItem.userID,
                    latestLocationItem.fullName,
                    formatDateTime(latestLocationItem.dateTime),
                    latestLocationItem.status,
                    "" // alertID - keeping empty as before
                )
                modalDialog.show(supportFragmentManager, "AssistanceModal")
            }
        }
    }

    private fun formatDateTime(dateTime: String): String {
        if (dateTime.isBlank()) return ""
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateTime)
            outputFormat.format(date)
        } catch (e: Exception) {
            ""
        }
    }

    // Called by AssistanceModalDialog after officer responds
    fun onOfficerResponded() {
        // UserSingleton.userID is not always set — fall back to SharedPreferences
        officerID = UserSingleton.userID
            ?: getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("userID", null)
        val id = officerID ?: run {
            Log.e("MapActivity", "Officer userID unavailable — cannot track GPS")
            return
        }

        if (officerLocationCallback != null) return

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2001)
            return
        }

        startOfficerGpsWriting(id)
    }

    private fun startOfficerGpsWriting(id: String) {
        if (officerLocationCallback != null) return

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        officerLocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                CoroutineScope(Dispatchers.IO).launch {
                    MySQLHelper.upsertLiveGPS(id, loc.latitude, loc.longitude, loc.accuracy)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedClient.requestLocationUpdates(request, officerLocationCallback!!, Looper.getMainLooper())
            Log.d("MapActivity", "Started officer GPS tracking for ID: $id")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            officerID?.let { startOfficerGpsWriting(it) }
        }
    }

    private fun stopOfficerGpsTracking() {
        officerLocationCallback?.let {
            LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it)
        }
        officerLocationCallback = null
        // Don't delete GPS data here — let it persist so the disabled user can still see it.
        // It gets cleaned up when the disabled user's polling detects the incident is resolved.
    }

    override fun onDestroy() {
        super.onDestroy()
        stopOfficerGpsTracking()
    }
}