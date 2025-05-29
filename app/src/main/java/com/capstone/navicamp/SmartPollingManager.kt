package com.capstone.navicamp

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*

class SmartPollingManager private constructor() {    private val TAG = "SmartPollingManager"
    
    // Adaptive intervals
    private val FAST_INTERVAL = 1000L      // 1 second when data is changing
    private val NORMAL_INTERVAL = 3000L    // 3 seconds normal
    private val SLOW_INTERVAL = 8000L      // 8 seconds when no changes
    private val VERY_SLOW_INTERVAL = 15000L // 15 seconds when inactive
    
    private val handler = Handler(Looper.getMainLooper())
    private var isPolling = false
    private var currentInterval = NORMAL_INTERVAL
    
    // Data change tracking
    private var lastAssistanceCount = -1
    private var lastVerificationCount = -1
    private var lastUserCount = -1
    private var lastDeviceCount = -1
    private var unchangedCycles = 0
    private var maxUnchangedCycles = 3
    
    // Callbacks
    var onDataUpdate: (() -> Unit)? = null
    var onConnectionStatusChange: ((Boolean) -> Unit)? = null
    
    companion object {
        @Volatile
        private var INSTANCE: SmartPollingManager? = null
        
        fun getInstance(): SmartPollingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartPollingManager().also { INSTANCE = it }
            }
        }
    }
    
    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (isPolling) {
                fetchDataAndAnalyze()
                handler.postDelayed(this, currentInterval)
            }
        }
    }
    
    fun startPolling() {
        if (!isPolling) {
            Log.d(TAG, "Starting smart polling")
            isPolling = true
            currentInterval = NORMAL_INTERVAL
            unchangedCycles = 0
            onConnectionStatusChange?.invoke(true)
            handler.post(pollingRunnable)
        }
    }
    
    fun stopPolling() {
        Log.d(TAG, "Stopping smart polling")
        isPolling = false
        handler.removeCallbacks(pollingRunnable)
        onConnectionStatusChange?.invoke(false)
    }
    
    private fun fetchDataAndAnalyze() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch current data
                val assistanceRequests = MySQLHelper.getPendingItems()
                val verificationRequests = MySQLHelper.getUnverifiedUsers()
                val userCount = MySQLHelper.getUserCount()
                val deviceCount = MySQLHelper.getDeviceCount()
                
                // Check for changes
                val assistanceChanged = assistanceRequests.size != lastAssistanceCount
                val verificationChanged = verificationRequests.size != lastVerificationCount
                val userCountChanged = userCount != lastUserCount
                val deviceCountChanged = deviceCount != lastDeviceCount
                
                val hasChanges = assistanceChanged || verificationChanged || 
                               userCountChanged || deviceCountChanged
                
                // Update last known values
                lastAssistanceCount = assistanceRequests.size
                lastVerificationCount = verificationRequests.size
                lastUserCount = userCount
                lastDeviceCount = deviceCount
                
                withContext(Dispatchers.Main) {
                    if (hasChanges) {
                        Log.d(TAG, "Data changes detected, updating UI")
                        unchangedCycles = 0
                        currentInterval = FAST_INTERVAL
                        onDataUpdate?.invoke()
                    } else {
                        unchangedCycles++
                        adjustPollingInterval()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching data: ${e.message}")
                withContext(Dispatchers.Main) {
                    // On error, slow down polling
                    currentInterval = SLOW_INTERVAL
                }
            }
        }
    }
    
    private fun adjustPollingInterval() {
        val newInterval = when {
            unchangedCycles <= 1 -> FAST_INTERVAL
            unchangedCycles <= maxUnchangedCycles -> NORMAL_INTERVAL
            unchangedCycles <= maxUnchangedCycles * 2 -> SLOW_INTERVAL
            else -> VERY_SLOW_INTERVAL
        }
        
        if (newInterval != currentInterval) {
            Log.d(TAG, "Adjusting polling interval from ${currentInterval}ms to ${newInterval}ms (unchanged cycles: $unchangedCycles)")
            currentInterval = newInterval
        }
    }
    
    // Call this when user performs an action that might cause data changes
    fun triggerFastUpdate() {
        Log.d(TAG, "Fast update triggered")
        unchangedCycles = 0
        currentInterval = FAST_INTERVAL
        
        // Immediately fetch data
        if (isPolling) {
            handler.removeCallbacks(pollingRunnable)
            handler.post(pollingRunnable)
        }
    }
    
    // Force immediate update
    fun forceUpdate() {
        if (isPolling) {
            fetchDataAndAnalyze()
        }
    }
    
    fun isActive(): Boolean = isPolling
    
    fun getCurrentInterval(): Long = currentInterval
}
