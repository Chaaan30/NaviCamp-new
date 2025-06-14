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
     * Show dialog to request battery optimization whitelist
     */
    fun showBatteryOptimizationDialog(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            return // Already whitelisted
        }
        
        AlertDialog.Builder(context)
            .setTitle("Enable Background Notifications")
            .setMessage("To receive assistance notifications when the app is closed, please disable battery optimization for NaviCamp.\n\nThis ensures you'll get notified immediately when help is needed.")
            .setPositiveButton("Open Settings") { _, _ ->
                requestIgnoreBatteryOptimizations(context)
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Open battery optimization settings for the app
     */
    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general battery optimization settings
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    // Last fallback to app settings
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                }
            }
        }
    }
    
    /**
     * Show manufacturer-specific battery optimization instructions
     */
    fun showManufacturerSpecificInstructions(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val instructions = when {
            manufacturer.contains("xiaomi") -> 
                "For Xiaomi devices:\n1. Go to Settings > Apps > Manage apps\n2. Find NaviCamp\n3. Enable 'Autostart'\n4. Set Battery saver to 'No restrictions'"
            
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "For Huawei/Honor devices:\n1. Go to Settings > Apps\n2. Find NaviCamp\n3. Enable 'Auto-launch'\n4. Go to Battery > App launch\n5. Set NaviCamp to 'Manage manually' and enable all options"
            
            manufacturer.contains("oppo") ->
                "For OPPO devices:\n1. Go to Settings > Battery > Battery Optimization\n2. Find NaviCamp and select 'Don't optimize'\n3. Go to Settings > Apps > NaviCamp\n4. Enable 'Allow background activity'"
            
            manufacturer.contains("vivo") ->
                "For Vivo devices:\n1. Go to Settings > Battery > Background App Refresh\n2. Find NaviCamp and enable it\n3. Go to Settings > Apps > NaviCamp\n4. Enable 'High background power consumption'"
            
            manufacturer.contains("samsung") ->
                "For Samsung devices:\n1. Go to Settings > Apps > NaviCamp\n2. Battery > Optimize battery usage\n3. Turn OFF optimization for NaviCamp\n4. Enable 'Allow background activity'"
            
            else ->
                "Please check your device's battery optimization settings and whitelist NaviCamp to ensure notifications work when the app is closed."
        }
        
        AlertDialog.Builder(context)
            .setTitle("Device-Specific Settings")
            .setMessage(instructions)
            .setPositiveButton("Got it") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
} 