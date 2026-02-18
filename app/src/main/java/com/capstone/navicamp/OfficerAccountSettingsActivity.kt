package com.capstone.navicamp

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.Properties
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class OfficerAccountSettingsActivity : AppCompatActivity() {
    // OTP / email fields
    private lateinit var sendOtpButton: Button
    private lateinit var confirmOtpButton: Button
    private lateinit var editOtp: EditText
    private lateinit var editEmail: EditText
    private var generatedOtp: String? = null
    private var isOtpConfirmed: Boolean = false
    private var loadingDialog: Dialog? = null

    private var isEditMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_officer_account_settings)

        // Header back button
        val btnBack: View? = findViewById(R.id.btn_back)
        btnBack?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        // Wire views
        sendOtpButton = findViewById(R.id.send_otp_button)
        confirmOtpButton = findViewById(R.id.confirm_otp_button)
        editOtp = findViewById(R.id.edit_otp)
        editEmail = findViewById(R.id.edit_email)

        val fullNameText = findViewById<TextView>(R.id.full_name_text)
        val editFullName = findViewById<EditText>(R.id.edit_full_name)
        val userIdText = findViewById<TextView>(R.id.school_id_text)
        val userTypeText = findViewById<TextView>(R.id.user_type_text)
        val emailText = findViewById<TextView>(R.id.email_text)
        val contactNumberText = findViewById<TextView>(R.id.contact_number_text)
        val editContactNumber = findViewById<EditText>(R.id.edit_contact_number)
        val dateCreatedText = findViewById<TextView>(R.id.date_created_text)
        val btnAction = findViewById<Button>(R.id.btnAction)

        // Load prefs
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", "")
        val userID = sharedPreferences.getString("userID", "")
        val userType = sharedPreferences.getString("userType", "")
        val email = sharedPreferences.getString("email", "")
        val contactNumber = sharedPreferences.getString("contactNumber", "")
        val createdOn = sharedPreferences.getString("createdOn", "")
        val updatedOn = sharedPreferences.getString("updatedOn", "")

        // Init display
        fullNameText.text = "Full Name: $fullName"
        userIdText.text = "User ID: $userID"
        userTypeText.text = "User Type: $userType"
        emailText.text = "Email: $email"
        contactNumberText.text = "Contact Number: $contactNumber"
        dateCreatedText.text = "Date Created: $createdOn"

        // Hide edit fields initially
        editFullName.visibility = View.GONE
        editEmail.visibility = View.GONE
        editContactNumber.visibility = View.GONE
        editOtp.visibility = View.GONE
        sendOtpButton.visibility = View.GONE
        confirmOtpButton.visibility = View.GONE

        btnAction.setOnClickListener {
            if (!isEditMode) {
                // Enter edit
                isEditMode = true
                btnAction.text = "SAVE CHANGES"

                editFullName.visibility = View.VISIBLE
                editFullName.setText(fullName)
                fullNameText.visibility = View.GONE

                editEmail.visibility = View.VISIBLE
                editEmail.setText(email)
                emailText.visibility = View.GONE

                editContactNumber.visibility = View.VISIBLE
                editContactNumber.setText(contactNumber)
                contactNumberText.visibility = View.GONE

                sendOtpButton.visibility = View.VISIBLE
            } else {
                // Save
                val newFull = editFullName.text.toString().trim()
                val newEmail = editEmail.text.toString().trim()
                val newContact = editContactNumber.text.toString().trim()
                val otp = editOtp.text.toString().trim()

                if (editEmail.visibility == View.VISIBLE && otp.isNotBlank()) {
                    if (otp == generatedOtp) isOtpConfirmed = true
                    else { Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                }

                if (newContact.isNotBlank() && newContact.length != 11) { Toast.makeText(this, "Contact number must be exactly 11 digits", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

                if (userID.isNullOrEmpty()) { Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

                showLoadingDialog()
                CoroutineScope(Dispatchers.Main).launch {
                    val updatedOn = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val result = withContext(Dispatchers.IO) {
                        MySQLHelper.updateUserWithUserID(
                            if (newFull.isNotBlank()) newFull else "",
                            if (isOtpConfirmed && newEmail.isNotBlank()) newEmail else "",
                            if (newContact.isNotBlank()) newContact else "",
                            "", // newEmergencyName (Placeholder for Officer)
                            "", // newEmergencyNumber (Placeholder for Officer)
                            userID!!,
                            updatedOn
                        )
                    }

                    dismissLoadingDialog()
                    if (result) {
                        Toast.makeText(this@OfficerAccountSettingsActivity, "Information updated successfully", Toast.LENGTH_SHORT).show()
                        val editor = sharedPreferences.edit()
                        if (newFull.isNotBlank()) { fullNameText.text = "Full Name: $newFull"; editor.putString("fullName", newFull) }
                        if (isOtpConfirmed && newEmail.isNotBlank()) { emailText.text = "Email: $newEmail"; editor.putString("email", newEmail) }
                        if (newContact.isNotBlank()) { contactNumberText.text = "Contact Number: $newContact"; editor.putString("contactNumber", newContact) }
                        editor.apply()

                        isEditMode = false
                        btnAction.text = "EDIT ACCOUNT DETAILS"

                        editFullName.visibility = View.GONE
                        editEmail.visibility = View.GONE
                        editContactNumber.visibility = View.GONE
                        editOtp.visibility = View.GONE
                        sendOtpButton.visibility = View.GONE
                        confirmOtpButton.visibility = View.GONE

                        fullNameText.visibility = View.VISIBLE
                        emailText.visibility = View.VISIBLE
                        contactNumberText.visibility = View.VISIBLE

                        finish(); startActivity(intent)
                    } else {
                        Toast.makeText(this@OfficerAccountSettingsActivity, "Failed to update information", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // OTP flows
        sendOtpButton.setOnClickListener {
            val newEmail = editEmail.text.toString().trim()
            if (newEmail.isBlank()) { Toast.makeText(this, "Please enter a new email", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (newEmail == email) { Toast.makeText(this, "The new email is the same as the current email", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            showLoadingDialog()
            editEmail.isEnabled = false

            CoroutineScope(Dispatchers.Main).launch {
                val emailExists = withContext(Dispatchers.IO) { MySQLHelper.isEmailExists(newEmail, userID!!) }
                if (emailExists) { dismissLoadingDialog(); editEmail.isEnabled = true; Toast.makeText(this@OfficerAccountSettingsActivity, "Email already exists in the database", Toast.LENGTH_SHORT).show() }
                else { generatedOtp = generateOtp(); withContext(Dispatchers.IO) { sendOtpEmail(newEmail, generatedOtp!!) }; dismissLoadingDialog(); editOtp.visibility = View.VISIBLE; confirmOtpButton.visibility = View.VISIBLE; Toast.makeText(this@OfficerAccountSettingsActivity, "OTP sent to email", Toast.LENGTH_SHORT).show() }
            }
        }

        confirmOtpButton.setOnClickListener {
            val otp = editOtp.text.toString().trim()
            if (otp == generatedOtp) { isOtpConfirmed = true; Toast.makeText(this, "OTP confirmed", Toast.LENGTH_SHORT).show(); editOtp.visibility = View.GONE; confirmOtpButton.visibility = View.GONE }
            else { Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun generateOtp(): String {
        val random = Random()
        val otp = StringBuilder()
        repeat(6) { otp.append(random.nextInt(10)) }
        return otp.toString()
    }

    private suspend fun sendOtpEmail(toEmail: String, otp: String) {
        withContext(Dispatchers.IO) {
            val username = "navicamp.noreply@gmail.com"
            val password = "tcwp hour hlzz tcag"
            val props = Properties().apply { put("mail.smtp.auth", "true"); put("mail.smtp.starttls.enable", "true"); put("mail.smtp.host", "smtp.gmail.com"); put("mail.smtp.port", "587") }
            val session = Session.getInstance(props, object : javax.mail.Authenticator() { override fun getPasswordAuthentication(): PasswordAuthentication { return PasswordAuthentication(username, password) } })
            try {
                val message = MimeMessage(session).apply { setFrom(InternetAddress(username)); setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail)); subject = "Your OTP Code"; setText("Your OTP code is: $otp") }
                Transport.send(message)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) { loadingDialog = Dialog(this).apply { setContentView(R.layout.dialog_loading); setCancelable(false); window?.setBackgroundDrawableResource(android.R.color.transparent) } }
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() { loadingDialog?.dismiss(); loadingDialog = null }

    override fun onResume() {
        super.onResume()
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", "Full Name")
        // no navigationView now; nothing to update
    }
}