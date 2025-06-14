package com.capstone.navicamp

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.card.MaterialCardView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import android.view.View
import android.widget.ProgressBar
import android.widget.ImageView
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.engine.GlideException
import android.graphics.drawable.Drawable
import com.bumptech.glide.load.DataSource
import kotlinx.coroutines.CoroutineScope
import java.util.Properties
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import com.capstone.navicamp.MySQLHelper
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class SecurityOfficerActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var assistanceLayout: LinearLayout
    private val viewModel: SecurityOfficerViewModel by viewModels()
    private lateinit var dataChangeReceiver: DataChangeReceiver
    
    // Smart Polling Manager for real-time updates
    private lateinit var smartPollingManager: SmartPollingManager

    // Declare the launcher for notification permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("Permission", "Notification permission granted")
                Toast.makeText(this, "Notification permission granted! You will now receive assistance alerts.", Toast.LENGTH_LONG).show()
            } else {
                Log.d("Permission", "Notification permission denied")
                Toast.makeText(this, "Notification permission denied. You may miss important assistance requests.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_officer)

        val registeredUsersCard = findViewById<MaterialCardView>(R.id.registered_users_card)
        registeredUsersCard.setOnClickListener {
            val intent = Intent(this, DisplayRegisteredUsersActivity::class.java)
            startActivity(intent)
        }

        // Initialize Smart Polling Manager
        smartPollingManager = SmartPollingManager.getInstance()
        setupSmartPollingListeners()

        fetchUnverifiedUsers()

        // Initialize navigationView
        navigationView = findViewById(R.id.navigation_view)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Dashboard"

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val incidentLogBtn: Button = findViewById(R.id.incidentLogBtn)
        incidentLogBtn.setOnClickListener {
            val intent = Intent(this, IncidentLogNew::class.java)
            startActivity(intent)
        }

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
                    // Navigate to OfficerAccountSettingsActivity
                    val intent = Intent(this, OfficerAccountSettingsActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.nav_item2 -> {
                    // Navigate to SecurityOfficerActivity and clear the activity stack
                    val intent = Intent(this, SecurityOfficerActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    true
                }

                else -> false
            }
        }

        // Initialize assistanceLayout
        assistanceLayout = findViewById(R.id.assistance_layout)

        // Initialize ProgressBars
        val noAssistanceProgress = findViewById<ProgressBar>(R.id.no_assistance_progress)
        val registeredUsersProgress = findViewById<ProgressBar>(R.id.registered_users_progress)
        val iotDevicesProgress = findViewById<ProgressBar>(R.id.iot_devices_progress)

        // Show ProgressBars
        noAssistanceProgress.visibility = View.VISIBLE
        registeredUsersProgress.visibility = View.VISIBLE
        iotDevicesProgress.visibility = View.VISIBLE

        // Observe the LiveData from the ViewModel
        viewModel.pendingItems.observe(this) { pendingItems ->
            updateAssistanceCards(pendingItems)
            findViewById<ProgressBar>(R.id.no_assistance_progress).visibility = View.GONE
        }

        viewModel.userCount.observe(this) { count ->
            findViewById<TextView>(R.id.registered_users).text = count.toString()
            findViewById<ProgressBar>(R.id.registered_users_progress).visibility = View.GONE
        }

        viewModel.deviceCount.observe(this) { count ->
            val iotDevicesTextView = findViewById<TextView>(R.id.iot_devices)
            iotDevicesTextView.text = count.toString()
            findViewById<ProgressBar>(R.id.iot_devices_progress).visibility = View.GONE
        }
        
        // Make the entire IoT devices card clickable
        findViewById<View>(R.id.iot_devices_card).setOnClickListener {
            val intent = Intent(this, WheelchairManagementActivity::class.java)
            startActivity(intent)
        }
        // Fetch the initial data
        viewModel.fetchPendingItems()
        viewModel.fetchUserCount()
        viewModel.fetchDeviceCount()

        // Register the BroadcastReceiver
        dataChangeReceiver = DataChangeReceiver {
            viewModel.fetchPendingItems()
            viewModel.fetchUserCount()
            viewModel.fetchDeviceCount()
        }
        val intentFilter = IntentFilter("com.capstone.navicamp.DATA_CHANGED")
        registerReceiver(dataChangeReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)

        // Ask for notification permission
        askNotificationPermission()
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level 33 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
                Log.d("Permission", "Notification permission is already granted")
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Show an explanation dialog
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Permission Needed")
                    .setMessage("This app needs notification permission to alert you when students request assistance. This is essential for security operations.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setNegativeButton("Not Now") { dialog, _ ->
                        dialog.dismiss()
                        Log.d("Permission", "User declined notification permission")
                    }
                    .show()
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }



    // Update onResume to start smart polling
    override fun onResume() {
        super.onResume()
        
        // Update user name - use SharedPreferences as fallback if singleton is empty
        val fullName = if (UserSingleton.fullName.isNullOrBlank()) {
            val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            val storedName = sharedPreferences.getString("fullName", "Officer")
            UserSingleton.fullName = storedName // Update singleton
            storedName
        } else {
            UserSingleton.fullName
        }
        
        findViewById<TextView>(R.id.secoff_fullname)?.text = fullName

        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = fullName
        }

        // Start smart polling for real-time updates
        smartPollingManager.startPolling()
        
        // Fetch initial data
        viewModel.fetchPendingItems()
        viewModel.fetchUserCount()
        viewModel.fetchDeviceCount()
        fetchUnverifiedUsers()
    }

    // Update onPause to pause polling but keep it ready for quick resume
    override fun onPause() {
        super.onPause()
        // Pause polling when activity is not visible to save battery
        smartPollingManager.stopPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop polling when activity is destroyed
        smartPollingManager.stopPolling()
        unregisterReceiver(dataChangeReceiver)
    }

    private fun fetchUnverifiedUsers() {
        lifecycleScope.launch {
            val unverifiedUsers = withContext(Dispatchers.IO) {
                MySQLHelper.getUnverifiedUsers()
            }
            updateVerificationCards(unverifiedUsers)
        }
    }

    private fun updateVerificationCards(users: List<UserData>) {
        val verificationLayout = findViewById<LinearLayout>(R.id.verification_layout)
        verificationLayout.removeAllViews()

        val noVerificationTextView = findViewById<TextView>(R.id.no_verification_text)
        val verificationSectionTitle = findViewById<TextView>(R.id.verification_section_title)
        val verificationScrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.verification_scroll_view)

        // Update section title with user count
        verificationSectionTitle.text = "Proof of Disability Verification (${users.size}):"

        if (users.isEmpty()) {
            noVerificationTextView.visibility = View.VISIBLE
            verificationScrollView.visibility = View.GONE
        } else {
            noVerificationTextView.visibility = View.GONE
            verificationScrollView.visibility = View.VISIBLE

            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

            for (user in users) {
                val cardView =
                    layoutInflater.inflate(R.layout.verification_card, verificationLayout, false)

                val userIdText = cardView.findViewById<TextView>(R.id.user_id_text)
                val fullNameText = cardView.findViewById<TextView>(R.id.full_name_text)
                val emailText = cardView.findViewById<TextView>(R.id.email_text)
                val contactNumberText = cardView.findViewById<TextView>(R.id.contact_number_text)
                val userTypeText = cardView.findViewById<TextView>(R.id.user_type_text)
                val createdOnText = cardView.findViewById<TextView>(R.id.created_on_text)
                val viewProofButton = cardView.findViewById<Button>(R.id.view_proof_button)

                userIdText.text = user.userID
                fullNameText.text = user.fullName
                emailText.text = user.email
                contactNumberText.text = user.contactNumber
                userTypeText.text = user.userType

                val date = inputFormat.parse(user.createdOn)
                val formattedDate = date?.let { dateFormat.format(it) } ?: user.createdOn
                val formattedTime = date?.let { timeFormat.format(it) } ?: user.createdOn
                createdOnText.text = "$formattedDate\n$formattedTime"

                viewProofButton.setOnClickListener {
                    showProofDialog(user)
                }

                verificationLayout.addView(cardView)
            }
        }
    }

    private fun showLoadingDialog(): AlertDialog {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()
        return dialog
    }

    private fun showProofDialog(user: UserData) {
        val loadingDialog = showLoadingDialog()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_proof_image, null)
        val proofImageView = dialogView.findViewById<ImageView>(R.id.proof_image_view)

        val s3BaseUrl = "https://navicampbucket.s3.amazonaws.com/"
        val fullImageUrl = s3BaseUrl + user.proofPicture
        Glide.with(this)
            .load(fullImageUrl)
            .apply(RequestOptions()
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.error)
                .timeout(60000) // Increase timeout to 60 seconds
            )
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    loadingDialog.dismiss()
                    // Show error dialog with more information
                    runOnUiThread {
                        AlertDialog.Builder(this@SecurityOfficerActivity)
                            .setTitle("Image Load Error")
                            .setMessage("Failed to load proof of disability image.\nReason: ${e?.rootCauses?.firstOrNull()?.message ?: "Unknown error"}")
                            .setPositiveButton("Retry") { _, _ ->
                                // Retry loading the image
                                showProofDialog(user)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    loadingDialog.dismiss()
                    return false
                }
            })
            .into(proofImageView)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Accept") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val isUpdated = withContext(Dispatchers.IO) {
                        MySQLHelper.updateUserVerificationStatus(user.userID, 1)
                    }
                    if (isUpdated) {
                        acceptedEmail(
                            user.email,
                            "Verification Accepted",
                            """
                        Dear User,

                        We are pleased to inform you that your proof of disability has been successfully verified. You may now log in to your account and access all available features.

                        Thank you,
                        CampusNavigator Team
                        """.trimIndent()
                        )
                          Toast.makeText(
                            this@SecurityOfficerActivity,
                            "User's proof of disability accepted and notified the user",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Trigger fast polling for immediate updates
                        SmartPollingManager.getInstance().triggerFastUpdate()
                    } else {
                        Toast.makeText(
                            this@SecurityOfficerActivity,
                            "Failed to update status",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Decline") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val isUpdated = withContext(Dispatchers.IO) {
                        MySQLHelper.updateUserVerificationStatus(user.userID, 2)
                    }
                    if (isUpdated) {
                        declinedEmail(
                            user.email,
                            "Verification Declined",
                            """
                        Dear User,

                        Unfortunately, your proof of disability was declined due to insufficient or unclear evidence. Please log in using your email and password, where you will be prompted to re-upload a valid proof of disability.

                        Thank you,
                        CampusNavigator Team
                        """.trimIndent()
                        )
                          Toast.makeText(
                            this@SecurityOfficerActivity,
                            "User's proof of disability declined and notified the user",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Trigger fast polling for immediate updates
                        SmartPollingManager.getInstance().triggerFastUpdate()
                    } else {
                        Toast.makeText(
                            this@SecurityOfficerActivity,
                            "Failed to update status",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNeutralButton("Close", null)
            .create()

        dialog.show()
    }

    private fun acceptedEmail(toEmail: String, subject: String, body: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val username = "navicamp.noreply@gmail.com"
            val password = "tcwp hour hlzz tcag"

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }

            val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })

            try {
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(username))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    setSubject(subject)
                    setText(body)
                }

                Transport.send(message)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun declinedEmail(toEmail: String, subject: String, body: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val username = "navicamp.noreply@gmail.com"
            val password = "tcwp hour hlzz tcag"

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }

            val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })

            try {
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(username))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    setSubject(subject)
                    setText(body)
                }

                Transport.send(message)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun updateUserVerificationStatus(userID: String, status: Int): Boolean {
        val success = withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            try {
                connection = MySQLHelper.getConnection() // Use MySQLHelper to access getConnection
                if (connection == null) {
                    println("Database connection failed.")
                    return@withContext false
                }
                val query = "UPDATE user_table SET verified = ? WHERE userID = ?"
                statement = connection.prepareStatement(query)
                statement.setInt(1, status)
                statement.setString(2, userID)
                val rowsAffected = statement.executeUpdate()
                rowsAffected > 0
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            } finally {
                statement?.close()
                connection?.close()
            }
        }
        return success
    }

    private suspend fun sendVerificationStatusEmail(toEmail: String, isAccepted: Boolean) {
        withContext(Dispatchers.IO) {
            val username = "navicamp.noreply@gmail.com"
            val password = "tcwp hour hlzz tcag" // App password

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }

            val session = Session.getInstance(props,
                object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(username, password)
                    }
                })

            try {
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(username))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    subject = if (isAccepted) "Verification Accepted" else "Verification Declined"
                    setText(if (isAccepted) "Your verification has been accepted." else "Your verification has been declined.")
                }
                Transport.send(message)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateAssistanceCards(pendingItems: List<LocationItem>) {
        assistanceLayout.removeAllViews()
        val noAssistanceTextView = findViewById<TextView>(R.id.no_assistance_text)
        val noAssistanceProgress = findViewById<ProgressBar>(R.id.no_assistance_progress)
        val assistanceSectionTitle = findViewById<TextView>(R.id.assistance_section_title)
        val assistanceScrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.assistance_scroll_view)

        // Update section title with count
        assistanceSectionTitle.text = "People currently in need of assistance (${pendingItems.size}):"

        if (pendingItems.isEmpty()) {
            noAssistanceTextView.visibility = View.VISIBLE
            assistanceScrollView.visibility = View.GONE
        } else {
            noAssistanceTextView.visibility = View.GONE
            assistanceScrollView.visibility = View.VISIBLE
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

            val sortedItems = pendingItems.sortedByDescending { item ->
                inputFormat.parse(item.dateTime)
            }

            // Take only the first 2 items
            val itemsToShow = sortedItems.take(2)

            // Add first 2 cards
            for (item in itemsToShow) {
                val cardView = LayoutInflater.from(this)
                    .inflate(R.layout.assistance_card, assistanceLayout, false)
                setupAssistanceCard(cardView, item, inputFormat, dateFormat, timeFormat)
                assistanceLayout.addView(cardView)
            }

            // If there are more items, add remaining cards
            if (sortedItems.size > 2) {
                // Add a divider
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        setMargins(16, 16, 16, 16)
                    }
                    setBackgroundColor(resources.getColor(R.color.primaryColor))
                    alpha = 0.2f
                    assistanceLayout.addView(this)
                }

                // Add remaining cards
                for (item in sortedItems.drop(2)) {
                    val cardView = LayoutInflater.from(this)
                        .inflate(R.layout.assistance_card, assistanceLayout, false)
                    setupAssistanceCard(cardView, item, inputFormat, dateFormat, timeFormat)
                    assistanceLayout.addView(cardView)
                }
            }
        }
        noAssistanceProgress.visibility = View.GONE
    }

    private fun setupAssistanceCard(
        cardView: View,
        item: LocationItem,
        inputFormat: SimpleDateFormat,
        dateFormat: SimpleDateFormat,
        timeFormat: SimpleDateFormat
    ) {
        val fullNameTextView = cardView.findViewById<TextView>(R.id.full_name_text)
        val createdOnDateTextView = cardView.findViewById<TextView>(R.id.created_on_date_text)
        val createdOnTimeTextView = cardView.findViewById<TextView>(R.id.created_on_time_text)
        val floorLevelTextView = cardView.findViewById<TextView>(R.id.floor_level_text)
        val officerRespondedTextView = cardView.findViewById<TextView>(R.id.officer_responded_text)
        val respondButton = cardView.findViewById<Button>(R.id.respond_button)

        fullNameTextView.text = item.fullName

        val date = inputFormat.parse(item.dateTime)
        val formattedDate = date?.let { dateFormat.format(it) } ?: item.dateTime
        val formattedTime = date?.let { timeFormat.format(it) } ?: item.dateTime
        createdOnDateTextView.text = formattedDate
        createdOnTimeTextView.text = formattedTime

        floorLevelTextView.text = item.floorLevel

        val officerName = item.officerName
        val currentOfficerName = UserSingleton.fullName
        
        // Handle officer response display and button state
        when {
            !officerName.isNullOrEmpty() && officerName == currentOfficerName -> {
                // Current officer has responded
                officerRespondedTextView.text = "Officer: $officerName (You)"
                officerRespondedTextView.visibility = View.VISIBLE
            }
            !officerName.isNullOrEmpty() && officerName != currentOfficerName -> {
                // Another officer has responded
                officerRespondedTextView.text = "Officer: $officerName"
                officerRespondedTextView.visibility = View.VISIBLE
            }
            else -> {
                // No officer has responded yet
                officerRespondedTextView.text = "No officer responded yet"
                officerRespondedTextView.visibility = View.VISIBLE
            }
        }
        
        // Button always shows "View on Map"
        respondButton.text = "View on Map"

        respondButton.setOnClickListener {
            val locationID = item.locationID
            
            lifecycleScope.launch(Dispatchers.IO) {
                // Get the latest location item data
                val locationItem = MySQLHelper.getLocationItemById(locationID)
                withContext(Dispatchers.Main) {
                    val intent = Intent(cardView.context, MapActivity::class.java).apply {
                        putExtra("OFFICER_NAME", currentOfficerName)
                        putExtra("LATITUDE", locationItem.latitude)
                        putExtra("LONGITUDE", locationItem.longitude)
                        putExtra("FLOOR_LEVEL", locationItem.floorLevel)
                        putExtra("LOCATION_ID", locationItem.locationID)
                        putExtra("USER_ID", locationItem.userID)
                        putExtra("FULL_NAME", locationItem.fullName)
                        putExtra("DATE_TIME", locationItem.dateTime)
                        putExtra("STATUS", locationItem.status)
                    }
                    cardView.context.startActivity(intent)
                }
            }
        }
    }

    private fun setupSmartPollingListeners() {
        // Set up smart polling data update callback
        smartPollingManager.onDataUpdate = {
            // Fetch fresh data when changes are detected
            viewModel.fetchPendingItems()
            viewModel.fetchUserCount() 
            viewModel.fetchDeviceCount()
            fetchUnverifiedUsers()
        }

        smartPollingManager.onConnectionStatusChange = { isConnected ->
            // You can add a connection status indicator here if needed
            Log.d("SecurityOfficer", "Polling status: ${if (isConnected) "Active" else "Inactive"}")
        }
    }

    // Handle click from XML onClick attribute
    fun onRegUsersCardClick(view: View) {
        val intent = Intent(this, DisplayRegisteredUsersActivity::class.java)
        startActivity(intent)
    }
}








