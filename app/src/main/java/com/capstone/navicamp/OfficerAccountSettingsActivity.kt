package com.capstone.navicamp

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
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

    private lateinit var emailEditContainer: View
    private lateinit var fullNameText: TextView
    private lateinit var editFullName: EditText
    private lateinit var userTypeText: TextView

    private lateinit var employeeTypeText: TextView
    private lateinit var emailText: TextView
    private lateinit var contactNumberText: TextView
    private lateinit var editContactNumber: EditText
    private lateinit var dateCreatedText: TextView
    private lateinit var btnAction: Button
    private var generatedOtp: String? = null
    private var isOtpConfirmed: Boolean = false
    private var loadingDialog: Dialog? = null

    private var isEditMode: Boolean = false

    // Real-time refresh variables
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshProfileData()
            mainHandler.postDelayed(this, 5000) // Poll every 5 seconds
        }
    }

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

        emailEditContainer = findViewById(R.id.email_edit_container)

        fullNameText = findViewById(R.id.full_name_text)
        editFullName = findViewById(R.id.edit_full_name)
        userTypeText = findViewById(R.id.user_type_text)
        employeeTypeText = findViewById(R.id.employee_type_text)
        emailText = findViewById(R.id.email_text)
        contactNumberText = findViewById(R.id.contact_number_text)
        editContactNumber = findViewById(R.id.edit_contact_number)
        dateCreatedText = findViewById(R.id.date_created_text)
        btnAction = findViewById(R.id.btnAction)

        // Load prefs
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", "")
        val userID = sharedPreferences.getString("userID", "")
        val userType = sharedPreferences.getString("userType", "")
        val position = sharedPreferences.getString("systemRole", "")
        val email = sharedPreferences.getString("email", "")
        val contactNumber = sharedPreferences.getString("contactNumber", "")
        val createdOn = sharedPreferences.getString("createdOn", "")
        val updatedOn = sharedPreferences.getString("updatedOn", "")

        // Init display
        fullNameText.text = "$fullName"
        userTypeText.text = "$userType"
        employeeTypeText.text = "$position"
        emailText.text = "$email"
        contactNumberText.text = "$contactNumber"
        dateCreatedText.text = "$createdOn"

        // Reset verification if email changes
        editEmail.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isOtpConfirmed = false
                editOtp.visibility = View.GONE
                confirmOtpButton.visibility = View.GONE
                sendOtpButton.text = "Verify"
                sendOtpButton.isEnabled = true
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

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
                setNonEditableFieldsDimmed(true)

                editFullName.visibility = View.VISIBLE
                editFullName.setText(fullName)
                fullNameText.visibility = View.GONE

                emailEditContainer.visibility = View.VISIBLE // Show the parent layout
                editEmail.visibility = View.VISIBLE         // ALSO show the actual text field
                editEmail.setText(emailText.text.toString())
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
                val currentEmail = emailText.text.toString().trim()
                val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                val userID = sharedPreferences.getString("userID", null)

                if (editEmail.visibility == View.VISIBLE && otp.isNotBlank()) {
                    if (otp == generatedOtp) isOtpConfirmed = true
                    else { Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                }

                if (newContact.isNotBlank() && newContact.length != 11) { Toast.makeText(this, "Contact number must be exactly 11 digits", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

                if (userID.isNullOrEmpty()) { Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

                if (newEmail != currentEmail && !isOtpConfirmed) {
                    Toast.makeText(this, "Please verify your new email first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newContact.length != 11) {
                    Toast.makeText(this, "Contact number must be 11 digits", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val finalEmail = if (isOtpConfirmed) newEmail else currentEmail

                showLoadingDialog()
                CoroutineScope(Dispatchers.Main).launch {
                    val updatedOn = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val result = withContext(Dispatchers.IO) {
                        MySQLHelper.updateOfficerWithUserID(
                            if (newFull.isNotBlank()) newFull else "",
                            if (isOtpConfirmed && newEmail.isNotBlank()) newEmail else "",
                            if (newContact.isNotBlank()) newContact else "",
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
                        emailEditContainer.visibility = View.GONE
                        editEmail.visibility = View.GONE
                        editContactNumber.visibility = View.GONE
                        editOtp.visibility = View.GONE
                        sendOtpButton.visibility = View.GONE
                        confirmOtpButton.visibility = View.GONE

                        fullNameText.visibility = View.VISIBLE
                        emailEditContainer.visibility = View.GONE
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
            if (otp == generatedOtp) { isOtpConfirmed = true; Toast.makeText(this, "OTP confirmed", Toast.LENGTH_SHORT).show(); editOtp.visibility = View.GONE; confirmOtpButton.visibility = View.GONE; sendOtpButton.text = "Verified ✓"
                sendOtpButton.isEnabled = false
                editEmail.isEnabled = false}
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

    private fun refreshProfileData() {
        if (isEditMode || !::fullNameText.isInitialized) return
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val userID = sharedPreferences.getString("userID", null) ?: return

        CoroutineScope(Dispatchers.Main).launch {
            val dbData = MySQLHelper.getOfficerProfile(userID)
            if (dbData != null) {
                fullNameText.text = "${dbData["fullName"]}"
                emailText.text = "${dbData["email"]}"
                contactNumberText.text = "${dbData["contactNumber"]}"
                userTypeText.text = "${dbData["userType"]}"
                employeeTypeText.text = "${dbData["systemRole"]}"
                dateCreatedText.text = "${dbData["createdOn"]}"

                setNonEditableFieldsDimmed(false)
            }
        }
    }

    private fun setNonEditableFieldsDimmed(dim: Boolean) {
        val color = if (dim) Color.parseColor("#AAAAAA") else Color.parseColor("#222222")
        val alpha = if (dim) 0.6f else 1.0f

        val nonEditable = listOf(userTypeText, employeeTypeText, dateCreatedText)
        nonEditable.forEach {
            it.setTextColor(color)
            it.alpha = alpha
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
        mainHandler.post(refreshRunnable)
        // no navigationView now; nothing to update
    }

    override fun onPause() {
        super.onPause()
        // Stop the loop when user leaves the activity to prevent background data usage
        mainHandler.removeCallbacks(refreshRunnable)
    }
}