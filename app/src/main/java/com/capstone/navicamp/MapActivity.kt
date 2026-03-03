package com.capstone.navicamp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fab: FloatingActionButton
    private var officerLocationCallback: LocationCallback? = null
    private var officerID: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

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

        val locationID = intent.getStringExtra("LOCATION_ID")
        if (!locationID.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                // Prefer live wheelchair coordinates from devices_table
                val deviceCoords = MySQLHelper.getDeviceCoordinatesByLocationID(locationID)
                withContext(Dispatchers.Main) {
                    if (deviceCoords != null) {
                        showAssistanceMarker(deviceCoords.first, deviceCoords.second)
                    } else {
                        showMapFromIntentExtras()
                    }
                }
            }
        } else {
            showMapFromIntentExtras()
        }

        // Show the bottom sheet
        showBottomSheet()
    }

    private fun showMapFromIntentExtras() {
        // Non-notification entry points may pass explicit coordinates.
        // No hardcoded fallback coordinates are used.
        val extras = intent.extras
        val latitude = (extras?.get("LATITUDE") as? Number)?.toDouble()
        val longitude = (extras?.get("LONGITUDE") as? Number)?.toDouble()

        if (latitude != null && longitude != null) {
            showAssistanceMarker(latitude, longitude)
        } else {
            Log.w("MapActivity", "No coordinates available from notification or intent extras.")
        }
    }

    private fun showAssistanceMarker(latitude: Double, longitude: Double) {
        val location = LatLng(latitude, longitude)
        map.clear()
        map.addMarker(MarkerOptions().position(location).title("Assistance Location"))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 17f))
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
                    "", // alertID - keeping empty as before
                    latestLocationItem.emergencyContactPerson,
                    latestLocationItem.emergencyContactNumber
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