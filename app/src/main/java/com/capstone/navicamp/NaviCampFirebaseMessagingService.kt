package com.capstone.navicamp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NaviCampFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // Use the title and body from Lambda function (which includes alertID)
            sendNotification(it.title ?: "NaviCamp", it.body ?: "", remoteMessage.data)
        }
        
        // Handle data-only messages (no notification payload)
        if (remoteMessage.notification == null && remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        
        // Send token to server
        sendTokenToServer(token)
    }

    private fun sendTokenToServer(token: String) {
        // Update FCM token in database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Try to get userID from UserSingleton first, then fallback to SharedPreferences
                var userID = UserSingleton.userID
                if (userID == null) {
                    val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                    userID = sharedPreferences.getString("userID", null)
                    Log.d(TAG, "UserSingleton.userID was null, retrieved from SharedPreferences: $userID")
                }

                if (userID != null) {
                    val success = MySQLHelper.updateUserFCMToken(userID, token)
                    if (success) {
                        Log.d(TAG, "FCM token updated successfully for user: $userID")
                    } else {
                        Log.e(TAG, "Failed to update FCM token for user: $userID")
                    }
                } else {
                    Log.w(TAG, "UserID is null in both UserSingleton and SharedPreferences, cannot update FCM token")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating FCM token: ${e.message}", e)
            }
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"]
        val alertID = data["alertID"] ?: "Unknown"
        
        when (type) {
            "assistance_request" -> {
                // Handle new assistance request notification
                val userName = data["fullName"] ?: "Unknown user"
                val floorLevel = data["floorLevel"] ?: "Unknown location"
                val deviceID = data["deviceID"] ?: "Unknown device"
                sendNotification(
                    "🚨 Assistance Request at $floorLevel",
                    "$userName needs help. (Device: $deviceID) [Alert: $alertID]",
                    data
                )
            }
            "officer_response" -> {
                // Handle officer response notification
                val officerName = data["officerName"] ?: "An officer"
                sendNotification(
                    "✅ Assistance Claimed",
                    "$officerName is responding to the request. [Alert: $alertID]",
                    data
                )
            }
            "officer_coming" -> {
                // Handle officer coming to help notification (for users who requested assistance)
                val officerName = data["officerName"] ?: "An officer"
                sendNotification(
                    "🚀 Help is Coming!",
                    "$officerName is coming to assist you. [Alert: $alertID]",
                    data
                )
            }
            "assistance_resolved" -> {
                // Handle assistance resolved notification
                val userName = data["userName"] ?: "A user"
                val floorLevel = data["floorLevel"] ?: "unknown location"
                sendNotification(
                    "✅ Assistance Resolved",
                    "$userName's assistance request on $floorLevel has been resolved. [Alert: $alertID]",
                    data
                )
            }
        }
    }

    private fun sendNotification(title: String, messageBody: String, data: Map<String, String>) {
        val intent = createNotificationIntent(data)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "navicamp_notifications"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "NaviCamp Assistance Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Notifications for assistance requests and responses"
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    private fun createNotificationIntent(data: Map<String, String>): Intent {
        val type = data["type"]
        val locationID = data["locationID"]
        
        return when (type) {
            "assistance_request", "officer_response" -> {
                // Open assistance details
                if (locationID != null) {
                    Intent(this, MapActivity::class.java).apply {
                        putExtra("LOCATION_ID", locationID)
                        putExtra("FROM_NOTIFICATION", true)
                    }
                } else {
                    Intent(this, SecurityOfficerActivity::class.java)
                }
            }
            "officer_coming" -> {
                // For users being notified that help is coming, open the main activity
                Intent(this, MainActivity::class.java).apply {
                    putExtra("FROM_NOTIFICATION", true)
                    putExtra("NOTIFICATION_TYPE", "officer_coming")
                    putExtra("OFFICER_NAME", data["officerName"])
                }
            }
            else -> {
                // Default to security officer dashboard
                Intent(this, SecurityOfficerActivity::class.java)
            }
        }
    }

    companion object {
        private const val TAG = "NaviCampFCM"
    }
} 