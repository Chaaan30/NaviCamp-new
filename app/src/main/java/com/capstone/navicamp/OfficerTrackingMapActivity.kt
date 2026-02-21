package com.capstone.navicamp

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfficerTrackingMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var officerMarker: Marker? = null
    private var userMarker: Marker? = null
    private var officerCircle: Circle? = null
    private var userCircle: Circle? = null

    private var officerUserID: String? = null
    private var myUserID: String? = null
    private var officerName: String? = null

    private val refreshHandler = Handler(Looper.getMainLooper())
    private var isTracking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_officer_tracking_map)

        officerName = intent.getStringExtra("OFFICER_NAME")
        officerUserID = intent.getStringExtra("OFFICER_USER_ID")
        myUserID = intent.getStringExtra("MY_USER_ID")

        findViewById<TextView>(R.id.tracking_officer_name).text =
            "${officerName ?: "Safety Officer"} is dispatched"

        findViewById<ImageView>(R.id.tracking_back_button).setOnClickListener { finish() }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.tracking_map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true
        startTracking()
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isTracking) return
            refreshLocations()
            refreshHandler.postDelayed(this, 3000)
        }
    }

    private fun startTracking() {
        isTracking = true
        refreshHandler.post(refreshRunnable)
    }

    private fun stopTracking() {
        isTracking = false
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun refreshLocations() {
        val oID = officerUserID ?: return
        val uID = myUserID ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val officerGps = MySQLHelper.getLiveGPS(oID)
            val userGps = MySQLHelper.getLiveGPS(uID)
            val stillActive = MySQLHelper.hasActiveIncidentForUser(uID)

            withContext(Dispatchers.Main) {
                if (!stillActive) {
                    Toast.makeText(this@OfficerTrackingMapActivity, "Assistance has been resolved", Toast.LENGTH_SHORT).show()
                    stopTracking()
                    finish()
                    return@withContext
                }
                updateMarkers(officerGps, userGps)
            }
        }
    }

    private fun updateMarkers(officerGps: DoubleArray?, userGps: DoubleArray?) {
        if (!::map.isInitialized) return

        // officerGps / userGps = [lat, lng, accuracyMeters]

        // Update officer marker + accuracy circle (blue)
        if (officerGps != null && officerGps[0] != 0.0) {
            val pos = LatLng(officerGps[0], officerGps[1])
            val acc = officerGps.getOrElse(2) { 0.0 }

            if (officerMarker == null) {
                officerMarker = map.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .title(officerName ?: "Safety Officer")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
            } else {
                officerMarker?.position = pos
            }
            updateAccuracyCircle(pos, acc, isOfficer = true)
        }

        // Update user marker + accuracy circle (red)
        if (userGps != null && userGps[0] != 0.0) {
            val pos = LatLng(userGps[0], userGps[1])
            val acc = userGps.getOrElse(2) { 0.0 }

            if (userMarker == null) {
                userMarker = map.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .title("Your Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
            } else {
                userMarker?.position = pos
            }
            updateAccuracyCircle(pos, acc, isOfficer = false)
        }

        fitBothMarkers()

        val statusText = findViewById<TextView>(R.id.tracking_status_text)
        statusText.text = when {
            officerGps == null || officerGps[0] == 0.0 -> "Waiting for officer's location..."
            else -> "Tracking live location..."
        }
    }

    private fun updateAccuracyCircle(center: LatLng, accuracyMeters: Double, isOfficer: Boolean) {
        // Always show a circle — use actual accuracy or a minimum of 20 m so it's always visible
        val radius = if (accuracyMeters > 20.0) accuracyMeters else 20.0

        if (isOfficer) {
            if (officerCircle == null) {
                officerCircle = map.addCircle(
                    CircleOptions()
                        .center(center)
                        .radius(radius)
                        .strokeWidth(2f)
                        .strokeColor(Color.argb(120, 66, 133, 244))   // blue outline
                        .fillColor(Color.argb(40, 66, 133, 244))      // light blue fill
                )
            } else {
                officerCircle?.center = center
                officerCircle?.radius = radius
            }
        } else {
            if (userCircle == null) {
                userCircle = map.addCircle(
                    CircleOptions()
                        .center(center)
                        .radius(radius)
                        .strokeWidth(2f)
                        .strokeColor(Color.argb(120, 234, 67, 53))    // red outline
                        .fillColor(Color.argb(40, 234, 67, 53))       // light red fill
                )
            } else {
                userCircle?.center = center
                userCircle?.radius = radius
            }
        }
    }

    private var hasInitiallyZoomed = false

    private fun fitBothMarkers() {
        val positions = listOfNotNull(officerMarker?.position, userMarker?.position)
        if (positions.size < 2) {
            // Zoom to whichever is available
            positions.firstOrNull()?.let {
                if (!hasInitiallyZoomed) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
                    hasInitiallyZoomed = true
                }
            }
            return
        }
        if (!hasInitiallyZoomed) {
            val bounds = LatLngBounds.Builder().apply { positions.forEach { include(it) } }.build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            hasInitiallyZoomed = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }
}
