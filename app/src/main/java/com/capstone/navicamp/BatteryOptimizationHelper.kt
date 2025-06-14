package com.capstone.navicamp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object BatteryOptimizationHelper {
    
    /**
     * Check if the app is whitelisted from battery optimization
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Battery optimization doesn't exist on older versions
        }
    }
    
    /**
     * Show general battery optimization instructions for all devices
     */
    fun showGeneralInstructions(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        var instructions = """
            To ensure you receive notifications when the app is closed:
            
            1. Go to Settings > Battery > Battery Optimization
            2. Find "NaviCamp" in the list
            3. Select "Don't optimize" or "Not optimized"
            4. Go back to Settings > Apps > NaviCamp
            5. Enable "Allow background activity"
            
            Note: Menu names may vary slightly between devices.
        """.trimIndent()

        if (manufacturer.contains("xiaomi")) {
            instructions += "\n\nFor Xiaomi Users:\nAlso find and disable 'Pause app activity if unused' to ensure the app is not stopped."
        }
        
        AlertDialog.Builder(context)
            .setTitle("Enable Background Notifications")
            .setMessage(instructions)
            .setPositiveButton("Open App Settings") { dialog, _ ->
                // Don't dismiss dialog here - it will be dismissed when user returns with settings changed
                openAppSettings(context)
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false) // Prevent dismissing by tapping outside
            .show()
    }



    /**
     * Show battery optimization dialog - simplified for all users
     */
    fun showBatteryOptimizationDialog(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            return // Already optimized, no need to show dialog
        }

        val prefs = context.getSharedPreferences("battery_optimization", Context.MODE_PRIVATE)
        val hasShown = prefs.getBoolean("battery_optimization_dialog_shown", false)

        if (!hasShown) {
            val dialog = AlertDialog.Builder(context)
                .setTitle("📱 Enable Notifications")
                .setMessage("To receive notifications when the app is closed, please configure your device settings.\n\nThis ensures you'll get notified immediately when needed.")
                .setPositiveButton("Open App Settings") { dialogInterface, _ ->
                    // Don't dismiss or mark as shown yet
                    openAppSettings(context)
                }
                .setNegativeButton("Later") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .setCancelable(false)
                .create()

            dialog.show()

            // Store reference to dialog for later dismissal
            currentDialog = dialog
        }
    }

    private var currentDialog: AlertDialog? = null

    /**
     * Check if settings were changed and dismiss dialog if needed
     */
    fun checkAndDismissDialog(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            currentDialog?.dismiss()
            currentDialog = null
            
            // Mark as shown only when actually disabled
            val prefs = context.getSharedPreferences("battery_optimization", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("battery_optimization_dialog_shown", true).apply()
        }
    }
    
    /**
     * Open app settings directly for all devices
     */
    @SuppressLint("BatteryLife")
    private fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings if app settings fail
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Should not happen, but just in case
                android.util.Log.e("BatteryOptimization", "Failed to open settings", e2)
            }
        }
    }
    


    /**
     * Reset battery optimization dialog flags for testing
     * Call this method to make the dialog appear again
     */
    fun resetDialogFlags(context: Context) {
        val prefs = context.getSharedPreferences("battery_optimization", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("battery_optimization_dialog_shown")
            apply()
        }
    }
} 