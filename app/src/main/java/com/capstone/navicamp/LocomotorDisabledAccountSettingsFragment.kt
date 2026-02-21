package com.capstone.navicamp

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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

class LocomotorAccountSettingsFragment : Fragment(R.layout.fragment_locomotor_disabled_account_settings) {
    // UI Components
    private lateinit var sendOtpButton: Button
    private lateinit var confirmOtpButton: Button
    private lateinit var editOtp: EditText
    private lateinit var editEmail: EditText
    private lateinit var emailEditContainer: View
    private lateinit var otpContainer: View

    private lateinit var fullNameText: TextView
    private lateinit var editFullName: EditText
    private lateinit var schoolIdText: TextView
    private lateinit var editSchoolId: EditText
    private lateinit var emailText: TextView
    private lateinit var contactNumberText: TextView
    private lateinit var editContactNumber: EditText

    // PWD Specific Display Fields
    private lateinit var emergencyNameText: TextView
    private lateinit var editEmergencyName: EditText
    private lateinit var emergencyNumberText: TextView
    private lateinit var editEmergencyNumber: EditText

    private lateinit var userTypeText: TextView
    private lateinit var disabilityTypeText: TextView
    private lateinit var verifiedByText: TextView
    private lateinit var verificationDateText: TextView
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
            mainHandler.postDelayed(this, 5000)
        }
    }

//    override fun onStart() {
//        super.onStart()
//        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
//        bottomNav?.visibility = View.GONE
//    }
//
//    override fun onStop() {
//        super.onStop()
//        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
//        bottomNav?.visibility = View.VISIBLE
//    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Header back button
        val btnBack: View? = view.findViewById(R.id.btn_back)
        btnBack?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 2. Wire views (FindViewById)
        sendOtpButton = view.findViewById(R.id.pwd_btn_send_otp)
        confirmOtpButton = view.findViewById(R.id.pwd_btn_confirm_otp)
        editOtp = view.findViewById(R.id.pwd_edit_otp)
        editEmail = view.findViewById(R.id.pwd_edit_email)
        emailEditContainer = view.findViewById(R.id.pwd_email_edit_container)
        otpContainer = view.findViewById(R.id.pwd_otp_container)

        fullNameText = view.findViewById(R.id.pwd_display_name)
        editFullName = view.findViewById(R.id.pwd_edit_name)
        schoolIdText = view.findViewById(R.id.pwd_display_id)
        editSchoolId = view.findViewById(R.id.pwd_edit_school_id)
        emailText = view.findViewById(R.id.pwd_display_email)
        contactNumberText = view.findViewById(R.id.pwd_display_contact)
        editContactNumber = view.findViewById(R.id.pwd_edit_contact)

        emergencyNameText = view.findViewById(R.id.pwd_display_emergencycontactname)
        editEmergencyName = view.findViewById(R.id.pwd_edit_emergencycontactname)
        emergencyNumberText = view.findViewById(R.id.pwd_display_emergencycontactnumber)
        editEmergencyNumber = view.findViewById(R.id.pwd_edit_emergencycontactnumber)

        userTypeText = view.findViewById(R.id.pwd_display_user_type)
        disabilityTypeText = view.findViewById(R.id.pwd_display_disability_type)
        verifiedByText = view.findViewById(R.id.pwd_display_verifiedby)
        verificationDateText = view.findViewById(R.id.pwd_display_verification_date)

        dateCreatedText = view.findViewById(R.id.pwd_display_created_date)
        btnAction = view.findViewById(R.id.btnAction)

        // 3. Load Initial Data
        loadInitialData()

        // 4. Email Change Listener
        editEmail.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isOtpConfirmed = false
                otpContainer.visibility = View.GONE
                confirmOtpButton.visibility = View.GONE
                sendOtpButton.text = "Verify"
                sendOtpButton.isEnabled = true
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // 5. Action Button (Edit / Save)
        btnAction.setOnClickListener {
            if (!isEditMode) {
                enterEditMode()
            } else {
                saveChanges()
            }
        }

        // 6. OTP Buttons
        sendOtpButton.setOnClickListener { sendOtp() }
        confirmOtpButton.setOnClickListener { confirmOtp() }
    }

    private fun loadInitialData() {
        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        fullNameText.text = sharedPreferences.getString("fullName", "")
        schoolIdText.text = sharedPreferences.getString("schoolID", "")
        emailText.text = sharedPreferences.getString("email", "")
        contactNumberText.text = sharedPreferences.getString("contactNumber", "")
        emergencyNameText.text = sharedPreferences.getString("emergencyContactName", "")
        emergencyNumberText.text = sharedPreferences.getString("emergencyContactNumber", "")
        userTypeText.text = sharedPreferences.getString("userType", "")
        disabilityTypeText.text = sharedPreferences.getString("disabilityType", "")
        verifiedByText.text = sharedPreferences.getString("verifiedBy", "")
        verificationDateText.text = sharedPreferences.getString("verificationDate", "")
        dateCreatedText.text = sharedPreferences.getString("createdOn", "")

        // Initial Visibility
        editFullName.visibility = View.GONE
        editSchoolId.visibility = View.GONE
        editEmail.visibility = View.GONE
        editContactNumber.visibility = View.GONE
        editEmergencyName.visibility = View.GONE
        editEmergencyNumber.visibility = View.GONE
        emailEditContainer.visibility = View.GONE
        otpContainer.visibility = View.GONE
        sendOtpButton.visibility = View.GONE
        confirmOtpButton.visibility = View.GONE
    }

    private fun enterEditMode() {
        isEditMode = true
        btnAction.text = "SAVE CHANGES"
        setNonEditableFieldsDimmed(true)

        val currentFull = fullNameText.text.toString()
        val currentSchoolId = schoolIdText.text.toString()
        val currentEmail = emailText.text.toString()
        val currentContact = contactNumberText.text.toString()
        val currentEmerName = emergencyNameText.text.toString()
        val currentEmerNumber = emergencyNumberText.text.toString()

        editFullName.apply { visibility = View.VISIBLE; setText(currentFull) }
        fullNameText.visibility = View.GONE

        editSchoolId.apply { visibility = View.VISIBLE; setText(currentSchoolId) }
        schoolIdText.visibility = View.GONE

        emailEditContainer.visibility = View.VISIBLE
        editEmail.apply { visibility = View.VISIBLE; setText(currentEmail) }
        emailText.visibility = View.GONE

        editContactNumber.apply { visibility = View.VISIBLE; setText(currentContact) }
        contactNumberText.visibility = View.GONE

        editEmergencyName.apply { visibility = View.VISIBLE; setText(currentEmerName) }
        emergencyNameText.visibility = View.GONE

        editEmergencyNumber.apply { visibility = View.VISIBLE; setText(currentEmerNumber) }
        emergencyNumberText.visibility = View.GONE

        sendOtpButton.visibility = View.VISIBLE
    }

    private fun saveChanges() {
        val newFull = editFullName.text.toString().trim()
        val newSchool = editSchoolId.text.toString().trim()
        val newEmail = editEmail.text.toString().trim()
        val otp = editOtp.text.toString().trim()
        val currentEmail = emailText.text.toString().trim()
        val newContact = editContactNumber.text.toString().trim()
        val newEmerName = editEmergencyName.text.toString().trim()
        val newEmerNumber = editEmergencyNumber.text.toString().trim()

        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userID = sharedPreferences.getString("userID", null)

        // Validations
        if (editEmail.visibility == View.VISIBLE && otp.isNotBlank()) {
            if (otp != generatedOtp) { Toast.makeText(requireContext(), "Invalid OTP", Toast.LENGTH_SHORT).show(); return }
        }
        if (newEmail != currentEmail && !isOtpConfirmed) {
            Toast.makeText(requireContext(), "Verify new email first", Toast.LENGTH_SHORT).show()
            return
        }
        if (newContact.length != 11 || (newEmerNumber.isNotBlank() && newEmerNumber.length != 11)) {
            Toast.makeText(requireContext(), "Phone numbers must be 11 digits", Toast.LENGTH_SHORT).show()
            return
        }

        showLoadingDialog()

        lifecycleScope.launch {
            val updatedOn = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val result = withContext(Dispatchers.IO) {
                MySQLHelper.updateUserWithUserID(
                    newFull, newSchool, if (isOtpConfirmed) newEmail else currentEmail,
                    newContact, newEmerName, newEmerNumber, userID!!, updatedOn
                )
            }

            dismissLoadingDialog()
            if (result) {
                val editor = sharedPreferences.edit()
                editor.putString("fullName", newFull)
                editor.putString("schoolID", newSchool)
                editor.putString("email", if (isOtpConfirmed) newEmail else currentEmail)
                editor.putString("contactNumber", newContact)
                editor.putString("emergencyContactName", newEmerName)
                editor.putString("emergencyContactNumber", newEmerNumber)
                editor.apply()

                Toast.makeText(requireContext(), "Updated Successfully", Toast.LENGTH_SHORT).show()
                exitEditMode()
            } else {
                Toast.makeText(requireContext(), "Update Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exitEditMode() {
        isEditMode = false
        btnAction.text = "EDIT ACCOUNT DETAILS"
        setNonEditableFieldsDimmed(false)

        editFullName.visibility = View.GONE
        editSchoolId.visibility = View.GONE
        emailEditContainer.visibility = View.GONE
        editEmail.visibility = View.GONE
        editContactNumber.visibility = View.GONE
        editEmergencyName.visibility = View.GONE
        editEmergencyNumber.visibility = View.GONE
        otpContainer.visibility = View.GONE
        sendOtpButton.visibility = View.GONE
        confirmOtpButton.visibility = View.GONE

        fullNameText.visibility = View.VISIBLE
        schoolIdText.visibility = View.VISIBLE
        emailText.visibility = View.VISIBLE
        contactNumberText.visibility = View.VISIBLE
        emergencyNameText.visibility = View.VISIBLE
        emergencyNumberText.visibility = View.VISIBLE

        loadInitialData()
    }

    private fun sendOtp() {
        val newEmail = editEmail.text.toString().trim()
        val userID = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).getString("userID", "")!!

        showLoadingDialog()
        lifecycleScope.launch {
            val emailExists = withContext(Dispatchers.IO) { MySQLHelper.isEmailExists(newEmail, userID) }
            dismissLoadingDialog()
            if (emailExists) {
                Toast.makeText(requireContext(), "Email already exists", Toast.LENGTH_SHORT).show()
            } else {
                generatedOtp = (100000..999999).random().toString()
                withContext(Dispatchers.IO) { sendOtpEmail(newEmail, generatedOtp!!) }
                otpContainer.visibility = View.VISIBLE
                confirmOtpButton.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "OTP sent to email", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmOtp() {
        if (editOtp.text.toString().trim() == generatedOtp) {
            isOtpConfirmed = true
            Toast.makeText(requireContext(), "Confirmed", Toast.LENGTH_SHORT).show()
            otpContainer.visibility = View.GONE
            confirmOtpButton.visibility = View.GONE
            sendOtpButton.text = "Verified ✓"
            sendOtpButton.isEnabled = false
            editEmail.isEnabled = false
        } else {
            Toast.makeText(requireContext(), "Invalid OTP", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun sendOtpEmail(toEmail: String, otp: String) {
        withContext(Dispatchers.IO) {
            val username = "navicamp.noreply@gmail.com"
            val password = "tcwp hour hlzz tcag"
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }
            val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
            })
            try {
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(username))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    subject = "Your OTP Code"
                    setText("Your OTP code is: $otp")
                }
                Transport.send(message)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun refreshProfileData() {
        if (isEditMode || !isAdded) return
        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userID = sharedPreferences.getString("userID", null) ?: return

        lifecycleScope.launch {
            val dbData = withContext(Dispatchers.IO) { MySQLHelper.getUserProfile(userID) }
            if (dbData != null && isAdded) {
                fullNameText.text = dbData["fullName"]
                schoolIdText.text = dbData["schoolID"]
                emailText.text = dbData["email"]
                contactNumberText.text = dbData["contactNumber"]
                emergencyNameText.text = dbData["emergencyName"]
                emergencyNumberText.text = dbData["emergencyNumber"]
                userTypeText.text = dbData["userType"]
                disabilityTypeText.text = dbData["disabilityType"]
                verifiedByText.text = dbData["verifiedBy"]
                verificationDateText.text = dbData["verificationDate"]
                dateCreatedText.text = dbData["createdOn"]

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
        val color = if (dim) Color.parseColor("#AAAAAA") else Color.parseColor("#222222")
        val alpha = if (dim) 0.6f else 1.0f
        listOf(userTypeText, disabilityTypeText, verifiedByText, verificationDateText, dateCreatedText).forEach {
            it.setTextColor(color)
            it.alpha = alpha
        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = Dialog(requireContext()).apply {
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

    override fun onResume() {
        super.onResume()
        mainHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(refreshRunnable)
    }
}