package com.capstone.navicamp

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class SetupCompleteFragment : Fragment() {
    
    private lateinit var titleTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var successIcon: ImageView
    private var initialPermissionState: Pair<Boolean, Boolean>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setup_complete, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleTextView = view.findViewById(R.id.titleTextView)
        descriptionTextView = view.findViewById(R.id.descriptionTextView)
        successIcon = view.findViewById(R.id.successIcon)

        view.findViewById<Button>(R.id.btnFinish).setOnClickListener {
            (activity as? SetupActivity)?.finishSetup()
        }
        
        // Check permissions initially and store the state
        val notificationsGranted = isNotificationPermissionGranted()
        val batteryOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(requireContext())
        initialPermissionState = Pair(notificationsGranted, batteryOptimized)
        
        updateUI(notificationsGranted, batteryOptimized)
    }

    override fun onResume() {
        super.onResume()
        
        // Only update if permissions have actually changed from initial state
        val currentNotifications = isNotificationPermissionGranted()
        val currentBattery = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(requireContext())
        
        initialPermissionState?.let { (initialNotifications, initialBattery) ->
            if (currentNotifications != initialNotifications || currentBattery != initialBattery) {
                // Permissions changed, update UI
                updateUI(currentNotifications, currentBattery)
                // Update stored state
                initialPermissionState = Pair(currentNotifications, currentBattery)
            }
        }
    }

    private fun updateUI(notificationsGranted: Boolean, batteryOptimized: Boolean) {
        if (notificationsGranted && batteryOptimized) {
            // Fully complete state
            titleTextView.text = "Setup Complete!"
            successIcon.setImageResource(R.drawable.ic_check_circle_large)
            successIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.green))
            descriptionTextView.text = "You're all set! You can run this setup again anytime from the sidebar menu after logging in."
        } else {
            // Incomplete state
            titleTextView.text = "Setup Finished"
            successIcon.setImageResource(R.drawable.ic_warning_large)
            successIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.orange))
            descriptionTextView.text = "Some permissions were skipped. For best performance, please complete the setup later from the sidebar menu."
        }
    }

    private fun checkPermissionsAndSetState() {
        val notificationsGranted = isNotificationPermissionGranted()
        val batteryOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(requireContext())
        updateUI(notificationsGranted, batteryOptimized)
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check both system permission AND if notifications are enabled for the app
            val systemPermissionGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            val notificationsEnabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
            
            systemPermissionGranted && notificationsEnabled
        } else {
            // For older Android versions, just check if notifications are enabled for the app
            NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        }
    }
} 