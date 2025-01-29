package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class OfficerAccountSettingsActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView
    private lateinit var progressBar: ProgressBar
    private lateinit var sendOtpButton: Button
    private lateinit var confirmOtpButton: Button
    private lateinit var editOtp: EditText
    private lateinit var editEmail: EditText
    private var generatedOtp: String? = null
    private var isOtpConfirmed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_officer_account_settings)

        // Initialize views
        sendOtpButton = findViewById(R.id.send_otp_button)
        confirmOtpButton = findViewById(R.id.confirm_otp_button)
        editOtp = findViewById(R.id.edit_otp)
        editEmail = findViewById(R.id.edit_email)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Officer Account Settings"

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Initialize navigationView
        navigationView = findViewById(R.id.navigation_view)

        // Initialize ProgressBar
        progressBar = findViewById(R.id.progressBar)

        // Retrieve data from SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", null)
        val userID = sharedPreferences.getString("userID", null)
        val userType = sharedPreferences.getString("userType", null)
        val email = sharedPreferences.getString("email", null)
        val contactNumber = sharedPreferences.getString("contactNumber", null)
        val createdOn = sharedPreferences.getString("createdOn", null)
        val updatedOn = sharedPreferences.getString("updatedOn", null)

        // Log the retrieved values
        Log.d("OfficerAccountSettingsActivity", "fullName: $fullName")
        Log.d("OfficerAccountSettingsActivity", "userID: $userID")
        Log.d("OfficerAccountSettingsActivity", "userType: $userType")
        Log.d("OfficerAccountSettingsActivity", "email: $email")
        Log.d("OfficerAccountSettingsActivity", "contactNumber: $contactNumber")
        Log.d("OfficerAccountSettingsActivity", "createdOn: $createdOn")
        Log.d("OfficerAccountSettingsActivity", "updatedOn: $updatedOn")

        // Format createdOn and updatedOn
        val formattedCreatedOn = createdOn?.takeIf { it.isNotEmpty() }?.let {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(it)
            outputFormat.format(date)
        }

        val formattedUpdatedOn = updatedOn?.takeIf { it.isNotEmpty() }?.let {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(it)
            outputFormat.format(date)
        }

        // Update UI with the retrieved data
        findViewById<TextView>(R.id.full_name_text)?.text = "Full Name: $fullName"
        findViewById<TextView>(R.id.user_id_text)?.text = "User ID: $userID"
        findViewById<TextView>(R.id.user_type_text)?.text = "User Type: $userType"
        findViewById<TextView>(R.id.email_text)?.text = "Email: $email"
        findViewById<TextView>(R.id.contact_number_text)?.text = "Contact Number: $contactNumber"
        findViewById<TextView>(R.id.date_created_text)?.text = "Date Created: $formattedCreatedOn"

        if (formattedUpdatedOn != null) {
            findViewById<TextView>(R.id.updated_on_text)?.text = "Updated On: $formattedUpdatedOn"
            findViewById<View>(R.id.updated_on_card)?.visibility = View.VISIBLE
        }

        // Set up NavigationView item click listener
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    // Clear SharedPreferences
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

        // Set up Update Info button click listener
        val updateInfoButton: Button = findViewById(R.id.update_info_button)
        val cancelEditButton: Button = findViewById(R.id.cancel_edit_button)
        val editFullName: EditText = findViewById(R.id.edit_full_name)
        val editContactNumber: EditText = findViewById(R.id.edit_contact_number)

        updateInfoButton.setOnClickListener {
            if (editFullName.visibility == View.VISIBLE || editEmail.visibility == View.VISIBLE || editContactNumber.visibility == View.VISIBLE) {
                val newFullName = editFullName.text.toString()
                val newEmail = editEmail.text.toString()
                val newContactNumber = editContactNumber.text.toString()
                val otp = editOtp.text.toString()

                Log.d("OfficerAccountSettingsActivity", "newFullName: $newFullName")
                Log.d("OfficerAccountSettingsActivity", "newEmail: $newEmail")
                Log.d("OfficerAccountSettingsActivity", "newContactNumber: $newContactNumber")
                Log.d("OfficerAccountSettingsActivity", "userID: $userID") // Log the userID

                if (editEmail.visibility == View.VISIBLE && otp.isNotBlank()) {
                    if (otp == generatedOtp) {
                        isOtpConfirmed = true
                    }
                }

                progressBar.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    val updatedOn = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val result = withContext(Dispatchers.IO) {
                        MySQLHelper.updateUserWithUserID(
                            if (newFullName.isNotBlank()) newFullName else "",
                            if (isOtpConfirmed && newEmail.isNotBlank()) newEmail else "",
                            if (newContactNumber.isNotBlank()) newContactNumber else "",
                            userID!!,
                            updatedOn
                        )
                    }
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Log.d("OfficerAccountSettingsActivity", "Update result: $result")
                        if (result) {
                            Toast.makeText(this@OfficerAccountSettingsActivity, "Information updated successfully", Toast.LENGTH_SHORT).show()
                            // Update the TextViews with the new values
                            if (newFullName.isNotBlank()) {
                                findViewById<TextView>(R.id.full_name_text).text = "Full Name: $newFullName"
                                sharedPreferences.edit().putString("fullName", newFullName).apply()
                            }
                            if (isOtpConfirmed && newEmail.isNotBlank()) {
                                findViewById<TextView>(R.id.email_text).text = "Email: $newEmail"
                                sharedPreferences.edit().putString("email", newEmail).apply()
                            }
                            if (newContactNumber.isNotBlank()) {
                                findViewById<TextView>(R.id.contact_number_text).text = "Contact Number: $newContactNumber"
                                sharedPreferences.edit().putString("contactNumber", newContactNumber).apply()
                            }
                            finish()
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@OfficerAccountSettingsActivity, "Failed to update information", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                editFullName.visibility = View.VISIBLE
                editEmail.visibility = View.VISIBLE
                editContactNumber.visibility = View.VISIBLE
                sendOtpButton.visibility = View.VISIBLE
                cancelEditButton.visibility = View.VISIBLE
            }
        }

        cancelEditButton.setOnClickListener {
            editFullName.visibility = View.GONE
            editEmail.visibility = View.GONE
            editContactNumber.visibility = View.GONE
            sendOtpButton.visibility = View.GONE
            editOtp.visibility = View.GONE
            confirmOtpButton.visibility = View.GONE
            cancelEditButton.visibility = View.GONE
        }

        sendOtpButton.setOnClickListener {
            val newEmail = editEmail.text.toString()
            if (newEmail.isNotBlank()) {
                progressBar.visibility = View.VISIBLE
                editEmail.isEnabled = false // Disable the email EditText
                CoroutineScope(Dispatchers.Main).launch {
                    generatedOtp = generateOtp()
                    withContext(Dispatchers.IO) {
                        sendOtpEmail(newEmail, generatedOtp!!)
                    }
                    progressBar.visibility = View.GONE
                    editOtp.visibility = View.VISIBLE
                    confirmOtpButton.visibility = View.VISIBLE
                    Toast.makeText(this@OfficerAccountSettingsActivity, "OTP sent to email", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a new email", Toast.LENGTH_SHORT).show()
            }
        }

        confirmOtpButton.setOnClickListener {
            val otp = editOtp.text.toString()
            if (otp == generatedOtp) {
                isOtpConfirmed = true
                Toast.makeText(this, "OTP confirmed", Toast.LENGTH_SHORT).show()
                editOtp.visibility = View.GONE
                confirmOtpButton.visibility = View.GONE
            } else {
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateOtp(): String {
        val random = Random()
        val otp = StringBuilder()
        for (i in 0 until 6) {
            otp.append(random.nextInt(10))
        }
        return otp.toString()
    }

    private suspend fun sendOtpEmail(toEmail: String, otp: String) {
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
                    subject = "Your OTP Code"
                    setText("Your OTP code is: $otp")
                }

                Transport.send(message)
                println("OTP email sent successfully")

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Retrieve the full name from SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", "Full Name")
        navigationView?.let {
            val headerView = it.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = fullName
        }
    }
}