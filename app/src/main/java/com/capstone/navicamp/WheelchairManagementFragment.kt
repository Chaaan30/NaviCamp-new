package com.capstone.navicamp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class WheelchairManagementFragment : Fragment(R.layout.fragment_wheelchair_management) {

    private lateinit var wheelchairsLayout: LinearLayout
    private lateinit var filterSpinner: Spinner
    private lateinit var searchEditText: EditText
    private lateinit var loadingProgress: ProgressBar
    private lateinit var noWheelchairsText: TextView
    private lateinit var wheelchairCountText: TextView
    private lateinit var fabAddDevice: ExtendedFloatingActionButton

    private var allWheelchairs = listOf<WheelchairDevice>()
    private var filteredWheelchairs = listOf<WheelchairDevice>()
    private var pollingJob: Job? = null
    private val POLLING_INTERVAL = 10000L
    private var isAdmin = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        wheelchairsLayout = view.findViewById(R.id.wheelchairs_layout)
        filterSpinner = view.findViewById(R.id.filter_spinner)
        searchEditText = view.findViewById(R.id.search_edit_text)
        loadingProgress = view.findViewById(R.id.loading_progress)
        noWheelchairsText = view.findViewById(R.id.no_wheelchairs_text)
        wheelchairCountText = view.findViewById(R.id.wheelchair_count)
        fabAddDevice = view.findViewById(R.id.fab_add_device)

        checkAdminStatus()
        setupFilters()
        loadWheelchairs()

        fabAddDevice.setOnClickListener { showAddDeviceDialog() }
    }

    private fun checkAdminStatus() {
        val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userID = prefs.getString("userID", null)?.trim()
        if (userID.isNullOrBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val access = withContext(Dispatchers.IO) {
                MySQLHelper.getVerificationGeneratorAccess(userID)
            }
            isAdmin = access?.isAdmin == true
            fabAddDevice.visibility = if (isAdmin) View.VISIBLE else View.GONE
        }
    }

    private fun setupFilters() {
        val filterOptions = arrayOf("All", "Available", "In Use", "Maintenance")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterSpinner.adapter = adapter

        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { filterWheelchairs() }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { filterWheelchairs() }
        })
    }

    private fun loadWheelchairs() {
        loadingProgress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            allWheelchairs = withContext(Dispatchers.IO) { MySQLHelper.getAllWheelchairs() }
            filteredWheelchairs = allWheelchairs
            updateWheelchairCards()
            loadingProgress.visibility = View.GONE
        }
    }

    private fun filterWheelchairs() {
        val selectedFilter = filterSpinner.selectedItem.toString()
        val searchQuery = searchEditText.text.toString().lowercase().trim()

        filteredWheelchairs = allWheelchairs.filter { wheelchair ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Available" -> wheelchair.status.equals("available", true)
                "In Use" -> wheelchair.status.equals("in_use", true) || wheelchair.userID != null
                "Maintenance" -> wheelchair.status.equals("maintenance", true)
                else -> true
            }
            val matchesSearch = searchQuery.isEmpty() || wheelchair.deviceID.lowercase().contains(searchQuery)
            matchesFilter && matchesSearch
        }
        updateWheelchairCards()
    }

    private fun updateWheelchairCards() {
        wheelchairsLayout.removeAllViews()
        wheelchairCountText.text = "Total Wheelchairs: ${filteredWheelchairs.size}"

        if (filteredWheelchairs.isEmpty()) {
            noWheelchairsText.visibility = View.VISIBLE
        } else {
            noWheelchairsText.visibility = View.GONE
            for (wheelchair in filteredWheelchairs) {
                val cardView = layoutInflater.inflate(R.layout.wheelchair_card, wheelchairsLayout, false)
                setupWheelchairCard(cardView, wheelchair)
                wheelchairsLayout.addView(cardView)
            }
        }
    }

    private fun getStatusDisplayText(status: String): String {
        return when (status.lowercase()) {
            "available" -> "Available"
            "in_use" -> "In Use"
            "maintenance" -> "Maintenance"
            "offline" -> "Offline"
            else -> status.uppercase()
        }
    }

    private fun setupWheelchairCard(view: View, wheelchair: WheelchairDevice) {
        val deviceName = wheelchair.deviceName?.takeIf { it.isNotBlank() } ?: "Wheelchair ${wheelchair.deviceID}"
        view.findViewById<TextView>(R.id.device_name_text).text = deviceName
        view.findViewById<TextView>(R.id.device_id_text).text = wheelchair.deviceID

        val statusText = view.findViewById<TextView>(R.id.status_text)
        statusText.text = getStatusDisplayText(wheelchair.status)

        // Set status badge style
        val statusBadgeBg: Int
        val statusTextColor: Int
        when (wheelchair.status.lowercase()) {
            "available" -> {
                statusBadgeBg = R.drawable.status_badge_available
                statusTextColor = R.color.status_available
            }
            "in_use" -> {
                statusBadgeBg = R.drawable.status_badge_in_use
                statusTextColor = R.color.status_in_use
            }
            "maintenance" -> {
                statusBadgeBg = R.drawable.status_badge_maintenance
                statusTextColor = R.color.status_maintenance
            }
            else -> {
                statusBadgeBg = R.drawable.status_badge_offline
                statusTextColor = R.color.status_offline
            }
        }
        statusText.setBackgroundResource(statusBadgeBg)
        statusText.setTextColor(ContextCompat.getColor(requireContext(), statusTextColor))

        // Set status indicator (top bar) color
        val indicator = view.findViewById<View>(R.id.status_indicator)
        val indicatorColor = when (wheelchair.status.lowercase()) {
            "available" -> R.color.status_available
            "in_use" -> R.color.status_in_use
            "maintenance" -> R.color.status_maintenance
            else -> R.color.status_offline
        }
        indicator.setBackgroundColor(ContextCompat.getColor(requireContext(), indicatorColor))

        // Set card background tint based on status
        val cardBg = when (wheelchair.status.lowercase()) {
            "available" -> R.drawable.card_border_available
            "in_use" -> R.drawable.card_border_in_use
            "maintenance" -> R.drawable.card_border_maintenance
            else -> R.drawable.card_border_offline
        }
        val card = view.findViewById<androidx.cardview.widget.CardView>(R.id.wheelchair_card)
        // Keep card white but set background on inner layout if needed

        // User info
        view.findViewById<TextView>(R.id.user_name_text).text =
            wheelchair.userName ?: "Not assigned"

        // Location info
        view.findViewById<TextView>(R.id.location_text).text =
            wheelchair.floorLevel ?: "Unknown"

        // Connection info
        val connectionText = view.findViewById<TextView>(R.id.connection_text)
        val connectionDot = view.findViewById<View>(R.id.connection_dot)
        if (wheelchair.connectedUntil != null) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val connectedDate = dateFormat.parse(wheelchair.connectedUntil)
                val displayFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                connectionText.text = "Until ${displayFormat.format(connectedDate!!)}"
                connectionDot.setBackgroundResource(R.drawable.status_badge_available)
            } catch (e: Exception) {
                connectionText.text = wheelchair.connectedUntil
                connectionDot.setBackgroundResource(R.drawable.status_badge_available)
            }
        } else {
            connectionText.text = "Not connected"
            connectionDot.setBackgroundResource(R.drawable.status_badge_offline)
        }

        // Maintenance Message
        val maintenanceMessageLayout = view.findViewById<LinearLayout>(R.id.maintenance_message_layout)
        val maintenanceMessageText = view.findViewById<TextView>(R.id.maintenance_message_text)
        if (wheelchair.status.equals("maintenance", ignoreCase = true) && !wheelchair.maintenanceMessage.isNullOrBlank()) {
            maintenanceMessageLayout.visibility = View.VISIBLE
            maintenanceMessageText.text = wheelchair.maintenanceMessage
        } else {
            maintenanceMessageLayout.visibility = View.GONE
        }

        // View details button color based on status
        val detailsBtn = view.findViewById<MaterialButton>(R.id.view_details_button)
        val btnColor = when (wheelchair.status.lowercase()) {
            "available" -> R.color.status_available
            "in_use" -> R.color.status_in_use
            "maintenance" -> R.color.status_maintenance
            else -> R.color.status_offline
        }
        detailsBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), btnColor))
        detailsBtn.setOnClickListener { showWheelchairDetails(wheelchair) }
    }

    private fun showWheelchairDetails(wheelchair: WheelchairDevice) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wheelchair_details, null)

        dialogView.findViewById<TextView>(R.id.detail_device_id).text = wheelchair.deviceID

        // Update status badge
        val statusBadge = dialogView.findViewById<TextView>(R.id.detail_status_badge)
        statusBadge.text = getStatusDisplayText(wheelchair.status)

        when (wheelchair.status.lowercase()) {
            "available" -> {
                statusBadge.setBackgroundResource(R.drawable.status_badge_available)
                statusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_available))
            }
            "in_use" -> {
                statusBadge.setBackgroundResource(R.drawable.status_badge_in_use)
                statusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_in_use))
            }
            "maintenance" -> {
                statusBadge.setBackgroundResource(R.drawable.status_badge_maintenance)
                statusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_maintenance))
            }
            else -> {
                statusBadge.setBackgroundResource(R.drawable.status_badge_offline)
                statusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_offline))
            }
        }

        // School ID instead of User ID
        dialogView.findViewById<TextView>(R.id.detail_user_id).text = wheelchair.schoolID ?: "None"
        dialogView.findViewById<TextView>(R.id.detail_user_name).text = wheelchair.userName ?: "No user assigned"
        dialogView.findViewById<TextView>(R.id.detail_floor_level).text = wheelchair.floorLevel ?: "Unknown"

        // Handle maintenance information
        val maintenanceReasonLayout = dialogView.findViewById<LinearLayout>(R.id.maintenance_reason_layout)
        val maintenanceReasonText = dialogView.findViewById<TextView>(R.id.detail_maintenance_reason)
        val setMaintenanceBtn = dialogView.findViewById<MaterialButton>(R.id.btn_set_maintenance)
        val removeMaintenanceBtn = dialogView.findViewById<MaterialButton>(R.id.btn_remove_maintenance)

        if (wheelchair.status.equals("maintenance", ignoreCase = true)) {
            maintenanceReasonLayout.visibility = View.VISIBLE
            maintenanceReasonText.text = wheelchair.maintenanceMessage ?: "No maintenance message provided"
            setMaintenanceBtn.visibility = View.GONE
            removeMaintenanceBtn.visibility = View.VISIBLE
        } else {
            maintenanceReasonLayout.visibility = View.GONE
            setMaintenanceBtn.visibility = View.VISIBLE
            removeMaintenanceBtn.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        setMaintenanceBtn.setOnClickListener {
            showSetMaintenanceDialog(wheelchair.deviceID, dialog)
        }

        removeMaintenanceBtn.setOnClickListener {
            showRemoveMaintenanceDialog(wheelchair.deviceID, dialog)
        }

        // Delete device button - admin only
        val deleteBtn = dialogView.findViewById<MaterialButton>(R.id.btn_delete_device)
        if (isAdmin) {
            deleteBtn.visibility = View.VISIBLE
            deleteBtn.setOnClickListener {
                showDeleteDeviceDialog(wheelchair.deviceID, dialog)
            }
        } else {
            deleteBtn.visibility = View.GONE
        }

        // Rename device button
        val renameBtn = dialogView.findViewById<MaterialButton>(R.id.btn_rename_device)
        renameBtn.setOnClickListener {
            showRenameDeviceDialog(wheelchair.deviceID, wheelchair.deviceName, dialog)
        }

        dialog.show()
    }

    private fun showDeleteDeviceDialog(deviceID: String, parentDialog: AlertDialog) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Device")
            .setMessage("Are you sure you want to delete device $deviceID? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) { MySQLHelper.deleteDevice(deviceID) }
                    if (success) {
                        Toast.makeText(requireContext(), "Device $deviceID deleted", Toast.LENGTH_SHORT).show()
                        loadWheelchairs()
                        parentDialog.dismiss()
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete device", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDeviceDialog(deviceID: String, currentName: String?, parentDialog: AlertDialog) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rename_device, null)
        val descriptionText = dialogView.findViewById<TextView>(R.id.rename_device_description)
        val nameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.rename_device_input)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel_rename)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm_rename)

        descriptionText.text = "Enter a new display name for device $deviceID."
        nameInput?.setText(currentName ?: "")
        nameInput?.setSelection(nameInput.text?.length ?: 0)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val newName = nameInput?.text.toString().trim()
            viewLifecycleOwner.lifecycleScope.launch {
                val success = withContext(Dispatchers.IO) { MySQLHelper.renameDevice(deviceID, newName) }
                if (success) {
                    Toast.makeText(requireContext(), "Device renamed", Toast.LENGTH_SHORT).show()
                    loadWheelchairs()
                    dialog.dismiss()
                    parentDialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Failed to rename device", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showAddDeviceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_device, null)

        val deviceIdText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.add_device_id)
        val deviceNameEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.add_device_name)
        val cancelBtn = dialogView.findViewById<MaterialButton>(R.id.btn_cancel_add)
        val saveDeviceBtn = dialogView.findViewById<MaterialButton>(R.id.btn_save_device)

        var nextDeviceID: String? = null

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Fetch next device ID
        viewLifecycleOwner.lifecycleScope.launch {
            nextDeviceID = withContext(Dispatchers.IO) { MySQLHelper.getNextDeviceID() }
            deviceIdText?.setText(nextDeviceID ?: "Error")
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }

        saveDeviceBtn.setOnClickListener {
            val deviceID = nextDeviceID
            val deviceName = deviceNameEdit?.text.toString().trim()
            if (deviceID == null) {
                Toast.makeText(requireContext(), "Device ID not ready yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val success = withContext(Dispatchers.IO) {
                    MySQLHelper.addDevice(deviceID, deviceName.ifBlank { null })
                }
                if (success) {
                    loadWheelchairs()
                    dialog.dismiss()
                    showQrGenerationDialog(deviceID)
                } else {
                    Toast.makeText(requireContext(), "Failed to add device", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showQrGenerationDialog(deviceID: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_device_qr, null)

        val deviceIdLabel = dialogView.findViewById<TextView>(R.id.qr_device_id_label)
        val qrCodeImage = dialogView.findViewById<ImageView>(R.id.qr_code_image)
        val generateQrBtn = dialogView.findViewById<MaterialButton>(R.id.btn_generate_qr)
        val doneBtn = dialogView.findViewById<MaterialButton>(R.id.btn_qr_done)

        deviceIdLabel.text = deviceID

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        generateQrBtn.setOnClickListener {
            try {
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(deviceID, BarcodeFormat.QR_CODE, 512, 512)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
                qrCodeImage.setImageBitmap(bitmap)
                qrCodeImage.visibility = View.VISIBLE

                generateQrBtn.text = "Save to Gallery"
                generateQrBtn.setIconResource(R.drawable.ic_check)
                generateQrBtn.setOnClickListener {
                    saveQrToGallery(bitmap, deviceID)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to generate QR code", Toast.LENGTH_SHORT).show()
            }
        }

        doneBtn.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun saveQrToGallery(bitmap: Bitmap, deviceID: String) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "QR_Device_$deviceID.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NaviCamp")
            }

            val resolver = requireContext().contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                Toast.makeText(requireContext(), "QR code saved to gallery", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(requireContext(), "Failed to save QR code", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSetMaintenanceDialog(deviceID: String, parentDialog: AlertDialog) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_maintenance, null)
        val messageInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.maintenance_message_input)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel_maintenance)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm_maintenance)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val message = messageInput?.text.toString().trim()
            setDeviceMaintenanceMode(deviceID, true, message, parentDialog)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showRemoveMaintenanceDialog(deviceID: String, parentDialog: AlertDialog) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Maintenance Mode")
            .setMessage("This will set the wheelchair back to available status.")
            .setPositiveButton("Remove Maintenance") { _, _ ->
                setDeviceMaintenanceMode(deviceID, false, null, parentDialog)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setDeviceMaintenanceMode(deviceID: String, isMode: Boolean, reason: String?, dialog: AlertDialog) {
        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) { MySQLHelper.updateDeviceMaintenanceStatus(deviceID, isMode, reason) }
            if (success) {
                loadWheelchairs()
                dialog.dismiss()
            }
        }
    }

    private fun startSmartPolling() {
        pollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val newOnes = withContext(Dispatchers.IO) { MySQLHelper.getAllWheelchairs() }
                if (newOnes != allWheelchairs) {
                    allWheelchairs = newOnes
                    filterWheelchairs()
                }
                delay(POLLING_INTERVAL)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startSmartPolling()
    }

    override fun onPause() {
        super.onPause()
        pollingJob?.cancel()
    }
}