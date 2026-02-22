package com.capstone.navicamp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class GpsSetupFragment : Fragment() {

    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var btnAction: Button

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateGpsStatus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gps_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusIcon = view.findViewById(R.id.statusIcon)
        statusText = view.findViewById(R.id.statusText)
        btnAction = view.findViewById(R.id.btnAction)

        btnAction.setOnClickListener {
            when {
                isGpsReady() -> (activity as? SetupActivity)?.moveToNextPage()
                !isLocationPermissionGranted() -> requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                else -> openLocationSettings()
            }
        }

        updateGpsStatus()
    }

    override fun onResume() {
        super.onResume()
        if (isAdded) {
            updateGpsStatus()
        }
    }

    private fun updateGpsStatus() {
        if (isGpsReady()) {
            statusIcon.setImageResource(R.drawable.ic_check_circle)
            statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.green))
            statusText.text = "GPS is enabled"
            statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            btnAction.text = "Next"
        } else {
            statusIcon.setImageResource(R.drawable.ic_location)
            statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.orange))
            statusText.text = "Enable location permission and GPS"
            statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
            btnAction.text = if (isLocationPermissionGranted()) "Open Location Settings" else "Allow Location Access"
        }
    }

    private fun isGpsReady(): Boolean {
        return isLocationPermissionGranted() && isLocationServiceEnabled()
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = requireContext().getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
}
