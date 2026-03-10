package com.capstone.navicamp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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
    private lateinit var legendContainer: LinearLayout
    private var officerLocationCallback: LocationCallback? = null
    private var officerID: String? = null
    private var selectedRequestMarker: Marker? = null
    private var officerSelfMarker: Marker? = null

    private val refreshHandler = Handler(Looper.getMainLooper())
    private var isRefreshing = false
    private val activeUserMarkers = mutableMapOf<String, Marker>()

    private val markerHues = listOf(
        BitmapDescriptorFactory.HUE_RED,
        BitmapDescriptorFactory.HUE_ORANGE,
        BitmapDescriptorFactory.HUE_YELLOW,
        BitmapDescriptorFactory.HUE_GREEN,
        BitmapDescriptorFactory.HUE_CYAN,
        BitmapDescriptorFactory.HUE_VIOLET,
        BitmapDescriptorFactory.HUE_MAGENTA,
        BitmapDescriptorFactory.HUE_ROSE
    )

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isRefreshing) return
            refreshRealtimeMarkers()
            refreshHandler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        officerID = UserSingleton.userID
            ?: getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("userID", null)

        // Initialize the map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize FAB
        fab = findViewById(R.id.fab)
        fab.setOnClickListener {
            showBottomSheet()
        }

        legendContainer = findViewById(R.id.legend_container)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        val locationID = intent.getStringExtra("LOCATION_ID")
        if (!locationID.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                // Use saved assistance coordinates (now sourced from user's phone GPS)
                val requestCoords = MySQLHelper.getDeviceCoordinatesByLocationID(locationID)
                withContext(Dispatchers.Main) {
                    if (requestCoords != null) {
                        showAssistanceMarker(requestCoords.first, requestCoords.second)
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

        startRealtimeRefresh()
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
        if (selectedRequestMarker == null) {
            selectedRequestMarker = map.addMarker(
                MarkerOptions()
                    .position(location)
                    .title("Selected Assistance")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        } else {
            selectedRequestMarker?.position = location
        }
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
                    latestLocationItem.contactNumber,
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

    private fun startRealtimeRefresh() {
        if (isRefreshing) return
        isRefreshing = true
        refreshHandler.post(refreshRunnable)
    }

    private fun stopRealtimeRefresh() {
        isRefreshing = false
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun refreshRealtimeMarkers() {
        val currentOfficerId = officerID
        CoroutineScope(Dispatchers.IO).launch {
            val activeUsers = MySQLHelper.getActiveAssistanceLiveGps()
            val officerGps = currentOfficerId?.let { MySQLHelper.getLiveGPS(it) }

            withContext(Dispatchers.Main) {
                if (!::map.isInitialized) return@withContext
                updateOfficerMarker(officerGps)
                updateActiveUserMarkers(activeUsers)
                updateLegend(activeUsers, officerGps != null)
            }
        }
    }

    private fun updateOfficerMarker(officerGps: DoubleArray?) {
        if (officerGps == null || officerGps[0] == 0.0) {
            officerSelfMarker?.remove()
            officerSelfMarker = null
            return
        }

        val position = LatLng(officerGps[0], officerGps[1])
        if (officerSelfMarker == null) {
            officerSelfMarker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("You (Officer)")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        } else {
            officerSelfMarker?.position = position
        }
    }

    private fun updateActiveUserMarkers(activeUsers: List<ActiveAssistanceGps>) {
        val latestIds = activeUsers.map { it.userID }.toSet()

        val staleMarkerIds = activeUserMarkers.keys.filter { it !in latestIds }
        staleMarkerIds.forEach { userID ->
            activeUserMarkers[userID]?.remove()
            activeUserMarkers.remove(userID)
        }

        activeUsers.forEach { userGps ->
            val userPosition = LatLng(userGps.latitude, userGps.longitude)
            val hue = markerHueForUser(userGps.userID)
            val existingMarker = activeUserMarkers[userGps.userID]

            if (existingMarker == null) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(userPosition)
                        .title("${userGps.fullName} (Needs Assistance)")
                        .snippet(userGps.userID)
                        .icon(BitmapDescriptorFactory.defaultMarker(hue))
                )
                if (marker != null) {
                    activeUserMarkers[userGps.userID] = marker
                }
            } else {
                existingMarker.position = userPosition
            }
        }
    }

    private fun markerHueForUser(userID: String): Float {
        val index = kotlin.math.abs(userID.hashCode()) % markerHues.size
        return markerHues[index]
    }

    private fun updateLegend(activeUsers: List<ActiveAssistanceGps>, hasOfficerGps: Boolean) {
        legendContainer.removeAllViews()

        if (hasOfficerGps) {
            legendContainer.addView(
                createLegendRow(
                    label = "You (Officer)",
                    colorInt = Color.HSVToColor(floatArrayOf(BitmapDescriptorFactory.HUE_AZURE, 1f, 1f))
                )
            )
        }

        activeUsers.forEach { userGps ->
            legendContainer.addView(
                createLegendRow(
                    label = userGps.fullName,
                    colorInt = Color.HSVToColor(floatArrayOf(markerHueForUser(userGps.userID), 1f, 1f))
                )
            )
        }

        legendContainer.visibility = if (legendContainer.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun createLegendRow(label: String, colorInt: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply {
                marginEnd = dpToPx(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorInt)
            }
        }

        val text = TextView(this).apply {
            this.text = label
            setTextColor(resources.getColor(R.color.black, theme))
            textSize = 12f
        }

        row.addView(dot)
        row.addView(text)
        return row
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

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
        stopRealtimeRefresh()
        stopOfficerGpsTracking()
    }
}