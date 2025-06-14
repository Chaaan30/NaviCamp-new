package com.capstone.navicamp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistanceActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var assistanceButton: Button
    private var loadingDialog: Dialog? = null

    // Permission launcher for notification permissions
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted! You will receive assistance updates.", Toast.LENGTH_SHORT).show()
        } else {
            // Show explanation dialog
            AlertDialog.Builder(this)
                .setTitle("Notification Permission Required")
                .setMessage("To receive assistance updates when help is on the way, please enable notifications in your device settings.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistance)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Request Assistance"

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Initialize navigationView
        navigationView = findViewById(R.id.navigation_view)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    // Clear SharedPreferences
                    val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.clear()
                    editor.apply()

                    // Navigate to MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_item1 -> {
                    // Navigate to AccountSettingsActivity
                    val intent = Intent(this, AccountSettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    true
                }
                R.id.nav_item2 -> {
                    // Navigate to LocomotorDisabilityActivity and clear the activity stack
                    val intent = Intent(this, LocomotorDisabilityActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }

        // Retrieve userID and fullName from SharedPreferences and set them in UserSingleton
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        UserSingleton.userID = sharedPreferences.getString("userID", null)
        DeviceSingleton.deviceID = sharedPreferences.getString("deviceID", null)
        UserSingleton.fullName = sharedPreferences.getString("fullName", null)

        // Log to verify UserSingleton is properly set for FCM token management
        Log.d("AssistanceActivity", "UserSingleton.userID set to: ${UserSingleton.userID}")

        // Request notification permission for receiving assistance updates
        requestNotificationPermission()

        // Register FCM token for this user
        registerFCMTokenForUser()

        // Set up the Request Assistance button
        assistanceButton = findViewById(R.id.requestAssistanceButton)

        assistanceButton.setOnClickListener {
            Log.d("AssistanceActivity", "Request Assistance button clicked")

            val userID = UserSingleton.userID
            val deviceID = DeviceSingleton.deviceID
            val fullName = UserSingleton.fullName

            Log.d("AssistanceActivity", "User ID: $userID, Device ID: $deviceID, Full Name: $fullName")

            if (userID != null && deviceID != null && fullName != null) {
                // Show loading dialog
                showLoadingDialog()

                CoroutineScope(Dispatchers.Main).launch {
                    val success = withContext(Dispatchers.IO) {
                        MySQLHelper.insertLocationData(this@AssistanceActivity, userID, deviceID, fullName)
                    }
                    if (success) {
                        Log.d("AssistanceActivity", "Location data inserted successfully")
                        Toast.makeText(this@AssistanceActivity, "Assistance requested successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("AssistanceActivity", "Failed to insert location data")
                        Toast.makeText(this@AssistanceActivity, "Failed to request assistance. Please try again.", Toast.LENGTH_SHORT).show()
                    }

                    // Dismiss loading dialog
                    dismissLoadingDialog()
                }
            } else {
                Log.e("AssistanceActivity", "User ID or Full Name is null")
                Toast.makeText(this, "User ID or Full Name is missing. Please log in again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    Log.d("NotificationPermission", "Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show explanation dialog
                    AlertDialog.Builder(this)
                        .setTitle("Notification Permission Needed")
                        .setMessage("This app needs notification permission to alert you when assistance is on the way. This helps ensure you don't miss important updates about your assistance requests.")
                        .setPositiveButton("Grant Permission") { _, _ ->
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Skip") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
                else -> {
                    // Request permission directly
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For Android versions below 13, notifications are automatically granted
            Log.d("NotificationPermission", "Android version below 13, notification permission not required")
        }
    }

    private fun registerFCMTokenForUser() {
        val userID = UserSingleton.userID
        if (userID != null) {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("AssistanceActivity", "Fetching FCM registration token failed", task.exception)
                        return@addOnCompleteListener
                    }

                    // Get new FCM registration token
                    val token = task.result
                    Log.d("AssistanceActivity", "FCM Token: $token")

                    // Update token in database
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val success = MySQLHelper.updateUserFCMToken(userID, token)
                            if (success) {
                                Log.d("AssistanceActivity", "FCM token updated successfully for user: $userID")
                            } else {
                                Log.e("AssistanceActivity", "Failed to update FCM token for user: $userID")
                            }
                        } catch (e: Exception) {
                            Log.e("AssistanceActivity", "Error updating FCM token: ${e.message}", e)
                        }
                    }
                }
        } else {
            Log.w("AssistanceActivity", "Cannot register FCM token - UserSingleton.userID is null")
        }
    }

    private fun showLoadingDialog() {
        loadingDialog = Dialog(this).apply {
            setContentView(R.layout.dialog_loading)
            setCancelable(false)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
    }

    override fun onResume() {
        super.onResume()
        // Retrieve the full name from SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", "Full Name")

        // Update nav_name_header in NavigationView
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = fullName
        }
    }
}