package com.capstone.navicamp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class NotificationPermissionFragment : Fragment() {
    
    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var btnAction: Button
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        updatePermissionStatus(isNotificationPermissionGranted())
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notification_permission, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        statusIcon = view.findViewById(R.id.statusIcon)
        statusText = view.findViewById(R.id.statusText)
        btnAction = view.findViewById(R.id.btnAction)

        btnAction.setOnClickListener {
            if (isNotificationPermissionGranted()) {
                (activity as? SetupActivity)?.moveToNextPage()
            } else {
                handleNotificationPermission()
            }
        }
        
        updatePermissionStatus(isNotificationPermissionGranted())
    }

    override fun onResume() {
        super.onResume()
        // Update status in case user changes it from settings and comes back
        if (isAdded) {
            updatePermissionStatus(isNotificationPermissionGranted())
        }
    }
    
    private fun handleNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val systemPermissionGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!systemPermissionGranted) {
                // Request system permission first
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // System permission granted but notifications are disabled in app settings
                openAppNotificationSettings()
            }
        } else {
            // For older Android versions, go directly to app settings
            openAppNotificationSettings()
        }
    }
    
    private fun openAppNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${requireContext().packageName}")
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings if app settings fail
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                // Should not happen, but just in case
                android.util.Log.e("NotificationPermission", "Failed to open settings", e2)
            }
        }
    }
    
    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check both system permission AND if notifications are enabled for the app
            val systemPermissionGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            val notificationsEnabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
            
            systemPermissionGranted && notificationsEnabled
        } else {
            // For older Android versions, just check if notifications are enabled for the app
            NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        }
    }
    
    private fun updatePermissionStatus(isGranted: Boolean) {
        if (isGranted) {
            statusIcon.setImageResource(R.drawable.ic_check_circle)
            statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.green))
            statusText.text = "Notifications Enabled"
            statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            btnAction.text = "Next"
        } else {
            statusIcon.setImageResource(R.drawable.ic_notifications_off)
            statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.orange))
            statusText.text = "Enable notifications to receive alerts"
            statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
            btnAction.text = "Enable Notifications"
        }
    }
} 