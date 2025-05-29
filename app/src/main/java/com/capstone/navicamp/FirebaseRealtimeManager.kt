package com.capstone.navicamp

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FirebaseRealtimeManager private constructor() {
    private val TAG = "FirebaseRealtime"
    private val firestore = FirebaseFirestore.getInstance()
    private var assistanceListener: ListenerRegistration? = null
    private var verificationListener: ListenerRegistration? = null
    private var statsListener: ListenerRegistration? = null
    
    // Listeners for different types of updates
    var onAssistanceUpdate: (() -> Unit)? = null
    var onVerificationUpdate: (() -> Unit)? = null
    var onStatsUpdate: (() -> Unit)? = null
    
    companion object {
        @Volatile
        private var INSTANCE: FirebaseRealtimeManager? = null
        
        fun getInstance(): FirebaseRealtimeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseRealtimeManager().also { INSTANCE = it }
            }
        }
    }
    
    fun startListening() {
        Log.d(TAG, "Starting Firebase real-time listeners")
        
        // Listen for assistance request changes
        assistanceListener = firestore.collection("notifications")
            .document("assistance_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to assistance requests", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Assistance requests updated")
                    onAssistanceUpdate?.invoke()
                }
            }
        
        // Listen for verification request changes
        verificationListener = firestore.collection("notifications")
            .document("verification_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to verification requests", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Verification requests updated")
                    onVerificationUpdate?.invoke()
                }
            }
        
        // Listen for stats changes (user/device counts)
        statsListener = firestore.collection("notifications")
            .document("stats")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to stats", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Stats updated")
                    onStatsUpdate?.invoke()
                }
            }
    }
    
    fun stopListening() {
        Log.d(TAG, "Stopping Firebase listeners")
        assistanceListener?.remove()
        verificationListener?.remove()
        statsListener?.remove()
        
        assistanceListener = null
        verificationListener = null
        statsListener = null
    }
    
    // Call this when data changes in your MySQL database
    fun triggerUpdate(type: String) {
        val docRef = firestore.collection("notifications").document(type)
        val updateData = mapOf(
            "lastUpdated" to System.currentTimeMillis(),
            "trigger" to true
        )
        
        docRef.set(updateData)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully triggered update for $type")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error triggering update for $type", e)
            }
    }
}
