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

class AccountSettingsActivity : AppCompatActivity() {
    // OTP / email fields
    private lateinit var pwdBtnSendOtp: Button
    private lateinit var pwdBtnConfirmOtp: Button
    private lateinit var pwdEditOtp: EditText
    private lateinit var pwdEditEmail: EditText
    private var generatedOtp: String? = null
    private var isOtpConfirmed: Boolean = false

    private var loadingDialog: Dialog? = null

    // Edit mode flag
    private var isEditMode: Boolean = false

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshProfileData()
            // Schedule the next refresh in 5 seconds (5000 milliseconds)
            mainHandler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)

        // Header back button
        val btnBack: View? = findViewById(R.id.btn_back)
        btnBack?.setOnClickListener {
            // Redirect to SettingsActivity
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Retrieve SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val userID = sharedPreferences.getString("userID", null)
        val fullName = sharedPreferences.getString("fullName", "")
        val email = sharedPreferences.getString("email", "")
        val contactNumber = sharedPreferences.getString("contactNumber", "")
        val emergencyContactName = sharedPreferences.getString("emergencyContactName", "")
        val emergencyContactNumber = sharedPreferences.getString("emergencyContactNumber", "")
        val userType = sharedPreferences.getString("userType", "")
        val disabilityType = sharedPreferences.getString("disabilityType", "")
        val verifiedBy = sharedPreferences.getString("verifiedBy", "")
        val verificationDate = sharedPreferences.getString("verificationDate", "")
        val createdOn = sharedPreferences.getString("createdOn", "")

        // Wire views (display and edit fields)
        val pwdDisplayName = findViewById<TextView>(R.id.pwd_display_name)
        val pwdEditName = findViewById<EditText>(R.id.pwd_edit_name)

        val pwdDisplayId = findViewById<TextView>(R.id.pwd_display_id)

        val pwdDisplayEmail = findViewById<TextView>(R.id.pwd_display_email)
        val pwdEmailEditContainer = findViewById<View>(R.id.pwd_email_edit_container)
        pwdEditEmail = findViewById(R.id.pwd_edit_email)
        pwdBtnSendOtp = findViewById(R.id.pwd_btn_send_otp)
        val pwdOtpContainer = findViewById<View>(R.id.pwd_otp_container)
        pwdEditOtp = findViewById(R.id.pwd_edit_otp)
        pwdBtnConfirmOtp = findViewById(R.id.pwd_btn_confirm_otp)

        val pwdDisplayContact = findViewById<TextView>(R.id.pwd_display_contact)
        val pwdEditContact = findViewById<EditText>(R.id.pwd_edit_contact)

        val pwdDisplayEmergencyName = findViewById<TextView>(R.id.pwd_display_emergencycontactname)
        val pwdEditEmergencyName = findViewById<EditText>(R.id.pwd_edit_emergencycontactname)
        val pwdDisplayEmergencyNumber = findViewById<TextView>(R.id.pwd_display_emergencycontactnumber)
        val pwdEditEmergencyNumber = findViewById<EditText>(R.id.pwd_edit_emergencycontactnumber)

        val pwdDisplayUserType = findViewById<TextView>(R.id.pwd_display_user_type)
        val pwdDisplayDisabilityType = findViewById<TextView>(R.id.pwd_display_disability_type)
        val pwdDisplayVerifiedBy = findViewById<TextView>(R.id.pwd_display_verifiedby)
        val pwdDisplayVerificationDate = findViewById<TextView>(R.id.pwd_display_verification_date)
        val pwdDisplayCreatedDate = findViewById<TextView>(R.id.pwd_display_created_date)

        val btnAction = findViewById<Button>(R.id.btnAction)

        // Initialize display values
        pwdDisplayName.text = fullName
        pwdDisplayId.text = userID
        pwdDisplayEmail.text = email
        pwdDisplayContact.text = contactNumber
        pwdDisplayEmergencyName.text = emergencyContactName
        pwdDisplayEmergencyNumber.text = emergencyContactNumber
        pwdDisplayUserType.text = userType
        pwdDisplayDisabilityType.text = disabilityType
        pwdDisplayVerifiedBy.text = verifiedBy
        pwdDisplayVerificationDate.text = verificationDate
        pwdDisplayCreatedDate.text = createdOn

        // Ensure edit fields are hidden initially
        pwdEditName.visibility = View.GONE
        pwdEmailEditContainer.visibility = View.GONE
        pwdEditContact.visibility = View.GONE
        pwdEditEmergencyName.visibility = View.GONE
        pwdEditEmergencyNumber.visibility = View.GONE
        pwdOtpContainer.visibility = View.GONE

        if (userID != null) {
            showLoadingDialog()
            CoroutineScope(Dispatchers.Main).launch {
                val dbData = MySQLHelper.getUserProfile(userID)
                dismissLoadingDialog()

                if (dbData != null) {
                    // Populate UI with fresh DB data
                    pwdDisplayName.text = dbData["fullName"]
                    pwdDisplayEmail.text = dbData["email"]
                    pwdDisplayContact.text = dbData["contactNumber"]
                    pwdDisplayEmergencyName.text = dbData["emergencyName"]
                    pwdDisplayEmergencyNumber.text = dbData["emergencyNumber"]
                    pwdDisplayUserType.text = dbData["userType"]
                    pwdDisplayDisabilityType.text = dbData["disabilityType"]
                    pwdDisplayVerifiedBy.text = dbData["verifiedBy"]
                    pwdDisplayVerificationDate.text = dbData["verificationDate"]
                    pwdDisplayCreatedDate.text = dbData["createdOn"]

                    // Sync SharedPreferences in case local cache was wrong
                    sharedPreferences.edit().apply {
                        putString("fullName", dbData["fullName"])
                        putString("email", dbData["email"])
                        putString("contactNumber", dbData["contactNumber"])
                        putString("emergencyContactName", dbData["emergencyName"])
                        putString("emergencyContactNumber", dbData["emergencyNumber"])
                        apply()
                    }
                } else {
                    Toast.makeText(this@AccountSettingsActivity, "Failed to load profile from DB", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Toggle behavior for bottom action button
        btnAction.setOnClickListener {
            if (!isEditMode) {
                // Enter edit mode
                isEditMode = true
                btnAction.text = "SAVE CHANGES"

                setNonEditableFieldsDimmed(true)

                // Hide display TextViews, show edit fields
                pwdDisplayName.visibility = View.GONE
                pwdEditName.visibility = View.VISIBLE
                pwdEditName.setText(pwdDisplayName.text?.toString() ?: "")

                pwdDisplayEmail.visibility = View.GONE
                pwdEmailEditContainer.visibility = View.VISIBLE
                pwdEditEmail.setText(pwdDisplayEmail.text?.toString() ?: "")

                pwdDisplayContact.visibility = View.GONE
                pwdEditContact.visibility = View.VISIBLE
                pwdEditContact.setText(pwdDisplayContact.text?.toString() ?: "")

                pwdDisplayEmergencyName.visibility = View.GONE
                pwdEditEmergencyName.visibility = View.VISIBLE
                pwdEditEmergencyName.setText(pwdDisplayEmergencyName.text?.toString() ?: "")

                pwdDisplayEmergencyNumber.visibility = View.GONE
                pwdEditEmergencyNumber.visibility = View.VISIBLE
                pwdEditEmergencyNumber.setText(pwdDisplayEmergencyNumber.text?.toString() ?: "")

                // Allow OTP flow
                pwdBtnSendOtp.visibility = View.VISIBLE

            } else {
                // Save changes
                val newFullName = pwdEditName.text.toString().trim()
                val newEmail = pwdEditEmail.text.toString().trim()
                val newContact = pwdEditContact.text.toString().trim()
                val newEmergencyName = pwdEditEmergencyName.text.toString().trim()
                val newEmergencyNumber = pwdEditEmergencyNumber.text.toString().trim()
                val otp = pwdEditOtp.text.toString().trim()

                if (pwdEmailEditContainer.visibility == View.VISIBLE && otp.isNotBlank()) {
                    if (otp == generatedOtp) {
                        isOtpConfirmed = true
                    } else {
                        Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }

                if (newContact.isNotBlank() && newContact.length != 11) {
                    Toast.makeText(this, "Contact number must be exactly 11 digits", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Perform DB update using existing helper
                if (userID == null) {
                    Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                showLoadingDialog()
                CoroutineScope(Dispatchers.Main).launch {
                    val updatedOn = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    // Logic: If email wasn't changed or OTP not confirmed, send the CURRENT display email
                    val finalEmail = if (isOtpConfirmed && newEmail.isNotBlank()) newEmail else pwdDisplayEmail.text.toString()
                    val result = withContext(Dispatchers.IO) {
                        // Reuse existing update helper; adapt to accept empty strings for unchanged fields
                        MySQLHelper.updateUserWithUserID(
                            if (newFullName.isNotBlank()) newFullName else "",
                            finalEmail,
                            if (newContact.isNotBlank()) newContact else "",
                            if (newEmergencyName.isNotBlank()) newEmergencyName else "",
                            if (newEmergencyNumber.isNotBlank()) newEmergencyNumber else "",
                            userID,
                            updatedOn
                        )
                    }

                    dismissLoadingDialog()
                    if (result) {
                        // Update local display fields and SharedPreferences
                        val prefsEditor = sharedPreferences.edit()
                        if (newFullName.isNotBlank()) {
                            pwdDisplayName.text = newFullName
                            prefsEditor.putString("fullName", newFullName)
                        }
                        if (isOtpConfirmed && newEmail.isNotBlank()) {
                            pwdDisplayEmail.text = newEmail
                            prefsEditor.putString("email", newEmail)
                        }
                        if (newContact.isNotBlank()) {
                            pwdDisplayContact.text = newContact
                            prefsEditor.putString("contactNumber", newContact)
                        }
                        if (newEmergencyName.isNotBlank()) {
                            pwdDisplayEmergencyName.text = newEmergencyName
                            prefsEditor.putString("emergencyContactName", newEmergencyName)
                        }
                        if (newEmergencyNumber.isNotBlank()) {
                            pwdDisplayEmergencyNumber.text = newEmergencyNumber
                            prefsEditor.putString("emergencyContactNumber", newEmergencyNumber)
                        }
                        prefsEditor.apply()

                        Toast.makeText(this@AccountSettingsActivity, "Information updated successfully", Toast.LENGTH_SHORT).show()

                        // Exit edit mode and reset UI
                        isEditMode = false
                        btnAction.text = "EDIT ACCOUNT DETAILS"

                        pwdEditName.visibility = View.GONE
                        pwdEmailEditContainer.visibility = View.GONE
                        pwdEditContact.visibility = View.GONE
                        pwdEditEmergencyName.visibility = View.GONE
                        pwdEditEmergencyNumber.visibility = View.GONE
                        pwdOtpContainer.visibility = View.GONE
                        pwdBtnSendOtp.visibility = View.GONE

                        pwdDisplayName.visibility = View.VISIBLE
                        pwdDisplayEmail.visibility = View.VISIBLE
                        pwdDisplayContact.visibility = View.VISIBLE
                        pwdDisplayEmergencyName.visibility = View.VISIBLE
                        pwdDisplayEmergencyNumber.visibility = View.VISIBLE

                        // Optionally restart activity to refresh other parts
                        finish()
                        startActivity(intent)

                    } else {
                        Toast.makeText(this@AccountSettingsActivity, "Failed to update information", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // OTP send/confirm listeners
        pwdBtnSendOtp.setOnClickListener {
            val newEmail = pwdEditEmail.text.toString().trim()
            if (newEmail.isBlank()) {
                Toast.makeText(this, "Please enter a new email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newEmail == pwdDisplayEmail.text.toString()) {
                Toast.makeText(this, "The new email is the same as the current email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showLoadingDialog()
            pwdEditEmail.isEnabled = false

            CoroutineScope(Dispatchers.Main).launch {
                val emailExists = withContext(Dispatchers.IO) {
                    MySQLHelper.isEmailExists(newEmail, userID!!)
                }
                if (emailExists) {
                    pwdEditEmail.isEnabled = true
                    dismissLoadingDialog()
                    Toast.makeText(this@AccountSettingsActivity, "Email already exists in the database", Toast.LENGTH_SHORT).show()
                } else {
                    generatedOtp = generateOtp()
                    withContext(Dispatchers.IO) {
                        sendOtpEmail(newEmail, generatedOtp!!)
                    }
                    dismissLoadingDialog()
                    pwdOtpContainer.visibility = View.VISIBLE
                    Toast.makeText(this@AccountSettingsActivity, "OTP sent to email", Toast.LENGTH_SHORT).show()
                }
            }
        }

        pwdBtnConfirmOtp.setOnClickListener {
            val otp = pwdEditOtp.text.toString().trim()
            if (otp == generatedOtp) {
                isOtpConfirmed = true
                Toast.makeText(this, "OTP confirmed", Toast.LENGTH_SHORT).show()
                pwdOtpContainer.visibility = View.GONE
            } else {
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun generateOtp(): String {
        val random = Random()
        val otp = StringBuilder()
        repeat(6) {
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

    private fun refreshProfileData() {
        // We only refresh if the user is NOT currently typing (Edit Mode)
        if (isEditMode) return

        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val userID = sharedPreferences.getString("userID", null) ?: return

        CoroutineScope(Dispatchers.Main).launch {
            val dbData = MySQLHelper.getUserProfile(userID)

            if (dbData != null) {
                // Update the TextViews only
                findViewById<TextView>(R.id.pwd_display_name).text = dbData["fullName"]
                findViewById<TextView>(R.id.pwd_display_email).text = dbData["email"]
                findViewById<TextView>(R.id.pwd_display_contact).text = dbData["contactNumber"]
                findViewById<TextView>(R.id.pwd_display_emergencycontactname).text = dbData["emergencyName"]
                findViewById<TextView>(R.id.pwd_display_emergencycontactnumber).text = dbData["emergencyNumber"]
                findViewById<TextView>(R.id.pwd_display_user_type).text = dbData["userType"]
                findViewById<TextView>(R.id.pwd_display_disability_type).text = dbData["disabilityType"]
                findViewById<TextView>(R.id.pwd_display_verifiedby).text = dbData["verifiedBy"]
                findViewById<TextView>(R.id.pwd_display_verification_date).text = dbData["verificationDate"]
                findViewById<TextView>(R.id.pwd_display_created_date).text = dbData["createdOn"]

                // Sync SharedPreferences in the background
                sharedPreferences.edit().apply {
                    putString("fullName", dbData["fullName"])
                    putString("email", dbData["email"])
                    putString("contactNumber", dbData["contactNumber"])
                    putString("emergencyContactName", dbData["emergencyName"])
                    putString("emergencyContactNumber", dbData["emergencyNumber"])
                    apply()
                }
            }
        }
    }

    private fun setNonEditableFieldsDimmed(dim: Boolean) {
        // Define the colors
        val valueColor = if (dim) Color.parseColor("#AAAAAA") else Color.parseColor("#222222")
        val labelAlpha = if (dim) 0.4f else 1.0f

        // List of TextViews that represent non-editable values
        val nonEditableValues = listOf(
            findViewById<TextView>(R.id.pwd_display_id),
            findViewById<TextView>(R.id.pwd_display_user_type),
            findViewById<TextView>(R.id.pwd_display_disability_type),
            findViewById<TextView>(R.id.pwd_display_verifiedby),
            findViewById<TextView>(R.id.pwd_display_verification_date),
            findViewById<TextView>(R.id.pwd_display_created_date)
        )

        // Apply color and alpha changes
        nonEditableValues.forEach { textView ->
            textView.setTextColor(valueColor)
            // Dim the parent (the layout containing the label and the value) if needed,
            // or just dim the text view itself:
            textView.alpha = if (dim) 0.6f else 1.0f
        }
    }

    override fun onResume() {
        super.onResume()
        // Start the automatic refresh loop
        mainHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        // Stop the loop when user leaves the activity to prevent background data usage
        mainHandler.removeCallbacks(refreshRunnable)
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = Dialog(this).apply {
                setContentView(R.layout.dialog_loading)
                setCancelable(false)
                window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

}