package com.capstone.navicamp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocomotorDisabledScanFragment : Fragment(R.layout.fragment_locomotor_disabled_scan) {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var statusDesc: TextView
    private lateinit var btnDisconnect: Button

    private var currentUserID: String? = null
    private var connectedDeviceID: String? = null
    private var isScanning = true

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) barcodeView.resume()
        else Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_LONG).show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        barcodeView = view.findViewById(R.id.barcode_view)
        statusDesc = view.findViewById(R.id.scan_status_desc)

        val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserID = prefs.getString("userID", null)

        // Set up the scan callback
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                // If a QR is found, isScanning is true, and we aren't already connected
                if (result?.text != null && isScanning && connectedDeviceID == null) {
                    isScanning = false
                    // IMMEDIATELY CONNECT without showing a duration dialog
                    connect(result.text)
                }
            }
        })

//        btnDisconnect.setOnClickListener { showDisconnectConfirm() }

        checkPermissionAndStart()
        checkCurrentConnection()
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            barcodeView.resume()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkCurrentConnection() {
        lifecycleScope.launch {
            val active = withContext(Dispatchers.IO) {
                MySQLHelper.getActiveConnectionForUser(currentUserID ?: "")
            }
            withContext(Dispatchers.Main) {
                if (!isAdded || view == null) return@withContext

                if (active != null) {
                    connectedDeviceID = active.deviceID
                    statusDesc.text = "Currently connected to: ${active.deviceID}"
                    barcodeView.visibility = View.GONE
                    //btnDisconnect.visibility = View.VISIBLE
                } else {
                    connectedDeviceID = null
                    statusDesc.text = "Align QR code within the frame to connect"
                    barcodeView.visibility = View.VISIBLE
                    //btnDisconnect.visibility = View.GONE
                    isScanning = true
                }
            }
        }
    }

    // Removed showDurationDialog function entirely

    fun refreshStateManually() {
        checkCurrentConnection()
    }

    private fun connect(id: String) {
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) { MySQLHelper.getDeviceStatus(id) }

            if (!isAdded) return@launch

            if (status != DeviceStatus.AVAILABLE) {
                Toast.makeText(requireContext(), "Device is currently $status", Toast.LENGTH_SHORT).show()
                isScanning = true
                return@launch
            }

            val success = withContext(Dispatchers.IO) {
                // Passing NULL for the expiry date to indicate an unlimited connection
                MySQLHelper.updateDeviceConnectionStatus(id, currentUserID, null)
            }

            if (success && isAdded) {
                (activity as? LocomotorDisabilityActivity)?.updateScanTabUI(true)

                (activity as? LocomotorDisabilityActivity)?.setBottomNavSelection(R.id.nav_pwd_home)

                // Switch to Home screen so user can use the SOS button immediately
                parentFragmentManager.beginTransaction()
                    .replace(R.id.pwd_fragment_container, LocomotorDisabledHomeFragment())
                    .commit()

                Toast.makeText(requireContext(), "Connected to $id", Toast.LENGTH_SHORT).show()
            } else {
                // If connection failed (e.g. device in use), allow scanning again
                Toast.makeText(requireContext(), "Device unavailable. Try another.", Toast.LENGTH_SHORT).show()
                isScanning = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }
}