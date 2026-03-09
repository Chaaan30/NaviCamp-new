package com.capstone.navicamp

import android.Manifest
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
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MapHomeFragment : Fragment(R.layout.fragment_map_home), OnMapReadyCallback {

    private var map: GoogleMap? = null
    private lateinit var legendContainer: LinearLayout
    private lateinit var fabAssistance: FloatingActionButton
    private var officerLocationCallback: LocationCallback? = null
    private var officerID: String? = null
    private var officerSelfMarker: Marker? = null

    private val refreshHandler = Handler(Looper.getMainLooper())
    private var isRefreshing = false
    private val activeUserMarkers = mutableMapOf<String, Marker>()

    // For navigating to a specific assistance location
    private var pendingLocationID: String? = null
    private var pendingLatitude: Double? = null
    private var pendingLongitude: Double? = null
    private var pendingFullName: String? = null
    private var pendingFloorLevel: String? = null
    private var pendingStatus: String? = null
    private var selectedRequestMarker: Marker? = null
    private var hasCenteredOnOfficer = false

    // Map of userID -> status for coloring markers
    private val activeUserStatuses = mutableMapOf<String, String>()
    // Map of userID -> locationID for opening modal on click
    private val activeUserLocationIDs = mutableMapOf<String, String>()

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isRefreshing) return
            refreshRealtimeMarkers()
            refreshHandler.postDelayed(this, 3000)
        }
    }

    companion object {
        private const val ARG_LOCATION_ID = "LOCATION_ID"
        private const val ARG_LATITUDE = "LATITUDE"
        private const val ARG_LONGITUDE = "LONGITUDE"
        private const val ARG_FULL_NAME = "FULL_NAME"
        private const val ARG_FLOOR_LEVEL = "FLOOR_LEVEL"
        private const val ARG_STATUS = "STATUS"

        fun newInstance(
            locationID: String? = null,
            latitude: Double? = null,
            longitude: Double? = null,
            fullName: String? = null,
            floorLevel: String? = null,
            status: String? = null
        ): MapHomeFragment {
            return MapHomeFragment().apply {
                arguments = Bundle().apply {
                    locationID?.let { putString(ARG_LOCATION_ID, it) }
                    latitude?.let { putDouble(ARG_LATITUDE, it) }
                    longitude?.let { putDouble(ARG_LONGITUDE, it) }
                    fullName?.let { putString(ARG_FULL_NAME, it) }
                    floorLevel?.let { putString(ARG_FLOOR_LEVEL, it) }
                    status?.let { putString(ARG_STATUS, it) }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        legendContainer = view.findViewById(R.id.legend_container)
        fabAssistance = view.findViewById(R.id.fab_assistance)

        officerID = UserSingleton.userID
            ?: requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
                .getString("userID", null)

        // Parse arguments for assistance navigation
        arguments?.let { args ->
            pendingLocationID = args.getString(ARG_LOCATION_ID)
            if (args.containsKey(ARG_LATITUDE)) pendingLatitude = args.getDouble(ARG_LATITUDE)
            if (args.containsKey(ARG_LONGITUDE)) pendingLongitude = args.getDouble(ARG_LONGITUDE)
            pendingFullName = args.getString(ARG_FULL_NAME)
            pendingFloorLevel = args.getString(ARG_FLOOR_LEVEL)
            pendingStatus = args.getString(ARG_STATUS)
        }

        // Show FAB only when navigating to a specific assistance
        if (pendingLocationID != null) {
            fabAssistance.visibility = View.VISIBLE
            fabAssistance.setOnClickListener { showBottomSheet() }
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map_fragment_container) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Set up marker click listener for assistance user markers
        googleMap.setOnMarkerClickListener { marker ->
            val clickedUserID = marker.snippet
            if (clickedUserID != null && activeUserLocationIDs.containsKey(clickedUserID)) {
                openAssistanceModalForUser(activeUserLocationIDs[clickedUserID]!!)
                true
            } else {
                false // default behavior for officer marker etc.
            }
        }

        val locationID = pendingLocationID
        if (!locationID.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                val requestCoords = MySQLHelper.getDeviceCoordinatesByLocationID(locationID)
                withContext(Dispatchers.Main) {
                    if (requestCoords != null) {
                        showAssistanceMarker(requestCoords.first, requestCoords.second)
                    } else {
                        showMapFromArgs()
                    }
                }
            }
            showBottomSheet()
        } else {
            // Default home: center on officer's own device GPS
            focusOnOfficerLocation()
        }

        startRealtimeRefresh()
    }

    private fun showMapFromArgs() {
        val latitude = pendingLatitude
        val longitude = pendingLongitude

        if (latitude != null && longitude != null) {
            showAssistanceMarker(latitude, longitude)
        } else {
            focusOnOfficerLocation()
        }
    }

    private fun focusOnOfficerLocation() {
        if (hasCenteredOnOfficer) return
        val ctx = context ?: return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Fall back to DB GPS
            officerID?.let { id ->
                CoroutineScope(Dispatchers.IO).launch {
                    val officerGps = MySQLHelper.getLiveGPS(id)
                    withContext(Dispatchers.Main) {
                        if (officerGps != null && officerGps[0] != 0.0) {
                            hasCenteredOnOfficer = true
                            val pos = LatLng(officerGps[0], officerGps[1])
                            setOfficerMarkerPosition(pos, moveCamera = true)
                        }
                    }
                }
            }
            return
        }
        val fusedClient = LocationServices.getFusedLocationProviderClient(ctx)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                hasCenteredOnOfficer = true
                val pos = LatLng(loc.latitude, loc.longitude)
                setOfficerMarkerPosition(pos, moveCamera = true)
            } else {
                // Fallback: try DB
                officerID?.let { id ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val officerGps = MySQLHelper.getLiveGPS(id)
                        withContext(Dispatchers.Main) {
                            if (officerGps != null && officerGps[0] != 0.0) {
                                hasCenteredOnOfficer = true
                                val pos2 = LatLng(officerGps[0], officerGps[1])
                                setOfficerMarkerPosition(pos2, moveCamera = true)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateOfficerMarkerFromDevice(moveCamera: Boolean) {
        val ctx = context ?: return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(ctx)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                setOfficerMarkerPosition(LatLng(loc.latitude, loc.longitude), moveCamera)
            }
        }
    }

    private fun setOfficerMarkerPosition(position: LatLng, moveCamera: Boolean) {
        val gMap = map ?: return
        if (officerSelfMarker == null) {
            officerSelfMarker = gMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("You (Officer)")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        } else {
            officerSelfMarker?.position = position
        }

        if (moveCamera) {
            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 17f))
        }
    }

    private fun showAssistanceMarker(latitude: Double, longitude: Double) {
        val gMap = map ?: return
        val location = LatLng(latitude, longitude)
        if (selectedRequestMarker == null) {
            selectedRequestMarker = gMap.addMarker(
                MarkerOptions()
                    .position(location)
                    .title("Selected Assistance")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        } else {
            selectedRequestMarker?.position = location
        }
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 17f))
    }

    private fun showBottomSheet() {
        val locationID = pendingLocationID ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val latestLocationItem = MySQLHelper.getLocationItemById(locationID)

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                val modalDialog = AssistanceModalDialog.newInstance(
                    latestLocationItem.floorLevel,
                    latestLocationItem.locationID,
                    latestLocationItem.userID,
                    latestLocationItem.fullName,
                    formatDateTime(latestLocationItem.dateTime),
                    latestLocationItem.status,
                    "",
                    latestLocationItem.emergencyContactPerson,
                    latestLocationItem.emergencyContactNumber
                )
                modalDialog.show(childFragmentManager, "AssistanceModal")
            }
        }
    }

    private fun openAssistanceModalForUser(locationID: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val item = MySQLHelper.getLocationItemById(locationID)

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                val modalDialog = AssistanceModalDialog.newInstance(
                    item.floorLevel,
                    item.locationID,
                    item.userID,
                    item.fullName,
                    formatDateTime(item.dateTime),
                    item.status,
                    "",
                    item.emergencyContactPerson,
                    item.emergencyContactNumber
                )
                modalDialog.show(childFragmentManager, "AssistanceModal")
            }
        }
    }

    private fun formatDateTime(dateTime: String): String {
        if (dateTime.isBlank()) return ""
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateTime)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            ""
        }
    }

    // Called by AssistanceModalDialog after officer responds
    fun onOfficerResponded() {
        val id = officerID ?: run {
            Log.e("MapHomeFragment", "Officer userID unavailable — cannot track GPS")
            return
        }

        if (officerLocationCallback != null) return

        val ctx = context ?: return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2001)
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
                val gMap = map ?: return@withContext
                if (!isAdded) return@withContext
                updateOfficerMarker(officerGps)
                if (officerGps == null || officerGps[0] == 0.0) {
                    updateOfficerMarkerFromDevice(moveCamera = false)
                }
                updateActiveUserMarkers(activeUsers)
                updateLegend(activeUsers, officerSelfMarker != null)
            }
        }
    }

    private fun updateOfficerMarker(officerGps: DoubleArray?) {
        if (officerGps == null || officerGps[0] == 0.0) {
            return
        }

        val position = LatLng(officerGps[0], officerGps[1])
        setOfficerMarkerPosition(position, moveCamera = false)
    }

    private fun updateActiveUserMarkers(activeUsers: List<ActiveAssistanceGps>) {
        val gMap = map ?: return
        val latestIds = activeUsers.map { it.userID }.toSet()

        val staleMarkerIds = activeUserMarkers.keys.filter { it !in latestIds }
        staleMarkerIds.forEach { userID ->
            activeUserMarkers[userID]?.remove()
            activeUserMarkers.remove(userID)
            activeUserStatuses.remove(userID)
            activeUserLocationIDs.remove(userID)
        }

        activeUsers.forEach { userGps ->
            val userPosition = LatLng(userGps.latitude, userGps.longitude)
            val hue = hueForStatus(userGps.status)
            val existingMarker = activeUserMarkers[userGps.userID]
            val prevStatus = activeUserStatuses[userGps.userID]

            // Track locationID for opening modal on marker click
            activeUserLocationIDs[userGps.userID] = userGps.locationID

            if (existingMarker == null) {
                val marker = gMap.addMarker(
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
                // Update color if status changed
                if (prevStatus != userGps.status) {
                    existingMarker.setIcon(BitmapDescriptorFactory.defaultMarker(hue))
                }
            }

            activeUserStatuses[userGps.userID] = userGps.status
        }
    }

    private fun hueForStatus(status: String): Float {
        return when (status.lowercase()) {
            "resolved" -> BitmapDescriptorFactory.HUE_GREEN
            "ongoing" -> BitmapDescriptorFactory.HUE_ORANGE
            else -> BitmapDescriptorFactory.HUE_RED // pending or anything else
        }
    }

    private fun colorForStatus(status: String): Int {
        return when (status.lowercase()) {
            "resolved" -> Color.HSVToColor(floatArrayOf(BitmapDescriptorFactory.HUE_GREEN, 1f, 1f))
            "ongoing" -> Color.HSVToColor(floatArrayOf(BitmapDescriptorFactory.HUE_ORANGE, 1f, 1f))
            else -> Color.HSVToColor(floatArrayOf(BitmapDescriptorFactory.HUE_RED, 1f, 1f))
        }
    }

    private fun updateLegend(activeUsers: List<ActiveAssistanceGps>, hasOfficerGps: Boolean) {
        val ctx = context ?: return
        legendContainer.removeAllViews()

        if (hasOfficerGps) {
            legendContainer.addView(
                createLegendRow(
                    label = "Officer (You)",
                    colorInt = Color.HSVToColor(floatArrayOf(BitmapDescriptorFactory.HUE_AZURE, 1f, 1f))
                )
            )
        }

        activeUsers.forEach { userGps ->
            val statusLabel = when (userGps.status.lowercase()) {
                "resolved" -> "RESOLVED"
                "ongoing" -> "ONGOING"
                else -> "PENDING"
            }
            legendContainer.addView(
                createLegendRow(
                    label = "${userGps.fullName} ($statusLabel)",
                    colorInt = colorForStatus(userGps.status)
                )
            )
        }

        legendContainer.visibility = if (legendContainer.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun createLegendRow(label: String, colorInt: Int): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }
        }

        val dot = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply {
                marginEnd = dpToPx(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorInt)
            }
        }

        val text = TextView(ctx).apply {
            this.text = label
            setTextColor(resources.getColor(R.color.black, ctx.theme))
            textSize = 12f
        }

        row.addView(dot)
        row.addView(text)
        return row
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun startOfficerGpsWriting(id: String) {
        if (officerLocationCallback != null) return
        val ctx = context ?: return

        val fusedClient = LocationServices.getFusedLocationProviderClient(ctx)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        officerLocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                CoroutineScope(Dispatchers.IO).launch {
                    MySQLHelper.upsertLiveGPS(id, loc.latitude, loc.longitude, loc.accuracy)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedClient.requestLocationUpdates(request, officerLocationCallback!!, Looper.getMainLooper())
            Log.d("MapHomeFragment", "Started officer GPS tracking for ID: $id")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            officerID?.let { startOfficerGpsWriting(it) }
        }
    }

    private fun stopOfficerGpsTracking() {
        val ctx = context ?: return
        officerLocationCallback?.let {
            LocationServices.getFusedLocationProviderClient(ctx).removeLocationUpdates(it)
        }
        officerLocationCallback = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopRealtimeRefresh()
        stopOfficerGpsTracking()
    }
}
