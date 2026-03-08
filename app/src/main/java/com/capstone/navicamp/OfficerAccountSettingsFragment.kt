package com.capstone.navicamp

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class OfficerAccountSettingsFragment : Fragment(R.layout.fragment_officer_account_settings) {
    // UI Components
    private lateinit var sendOtpButton: Button
    private lateinit var confirmOtpButton: Button
    private lateinit var editOtp: EditText
    private lateinit var editEmail: EditText
    private lateinit var emailEditContainer: View
    private lateinit var fullNameText: TextView
    private lateinit var editFullName: EditText
    private lateinit var userTypeText: TextView
    private lateinit var employeeTypeText: TextView
    private lateinit var departmentText: TextView
    private lateinit var editDepartmentSpinner: Spinner
    private lateinit var emailText: TextView
    private lateinit var contactNumberText: TextView
    private lateinit var editContactNumber: EditText
    private lateinit var dateCreatedText: TextView
    private lateinit var btnAction: Button
    private lateinit var editFullNameLayout: TextInputLayout
    private lateinit var editContactNumberLayout: TextInputLayout
    private lateinit var otpContainer: View

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
//        // Find the navbar in the Activity and hide it
//        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
//        bottomNav?.visibility = View.GONE
//    }
//
//    override fun onStop() {
//        super.onStop()
//        // Show the navbar again when leaving this fragment
//        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
//        bottomNav?.visibility = View.GONE
//    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Header back button (Switches back to Home Tab)
        val btnBack: View? = view.findViewById(R.id.btn_back)
        btnBack?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 2. Wire views
        sendOtpButton = view.findViewById(R.id.send_otp_button)
        confirmOtpButton = view.findViewById(R.id.confirm_otp_button)
        editOtp = view.findViewById(R.id.edit_otp)
        editEmail = view.findViewById(R.id.edit_email)
        emailEditContainer = view.findViewById(R.id.email_edit_container)

        fullNameText = view.findViewById(R.id.full_name_text)
        editFullName = view.findViewById(R.id.edit_full_name)
        userTypeText = view.findViewById(R.id.user_type_text)
        employeeTypeText = view.findViewById(R.id.employee_type_text)
        departmentText = view.findViewById(R.id.department_text)
        editDepartmentSpinner = view.findViewById(R.id.edit_department_spinner)
        emailText = view.findViewById(R.id.email_text)
        contactNumberText = view.findViewById(R.id.contact_number_text)
        editContactNumber = view.findViewById(R.id.edit_contact_number)

        dateCreatedText = view.findViewById(R.id.date_created_text)
        editFullNameLayout = view.findViewById(R.id.edit_full_name_layout)
        editContactNumberLayout = view.findViewById(R.id.edit_contact_number_layout)
        otpContainer = view.findViewById(R.id.otp_container)
        btnAction = view.findViewById(R.id.btnAction)

        // 3. Load Initial Data
        loadInitialData()

        // 4. Email Change Listener
        editEmail.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isOtpConfirmed = false
                otpContainer.visibility = View.GONE
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
        userTypeText.text = sharedPreferences.getString("userType", "")
        employeeTypeText.text = sharedPreferences.getString("systemRole", "")
        departmentText.text = sharedPreferences.getString("department", "")
        emailText.text = sharedPreferences.getString("email", "")
        contactNumberText.text = sharedPreferences.getString("contactNumber", "")
        dateCreatedText.text = sharedPreferences.getString("createdOn", "")

        // Initial Visibility
        editFullNameLayout.visibility = View.GONE
        editDepartmentSpinner.visibility = View.GONE
        emailEditContainer.visibility = View.GONE
        editContactNumberLayout.visibility = View.GONE
        otpContainer.visibility = View.GONE
        sendOtpButton.visibility = View.GONE
    }

    private fun enterEditMode() {
        isEditMode = true
        btnAction.text = "SAVE CHANGES"
        setNonEditableFieldsDimmed(true)

        val currentEmail = emailText.text.toString()
        val currentFull = fullNameText.text.toString()
        val currentContact = contactNumberText.text.toString()
        val currentDepartment = departmentText.text.toString()

        editFullNameLayout.visibility = View.VISIBLE
        editFullName.setText(currentFull)
        fullNameText.visibility = View.GONE

        emailEditContainer.visibility = View.VISIBLE
        editEmail.setText(currentEmail)
        editEmail.isEnabled = true
        emailText.visibility = View.GONE

        sendOtpButton.text = "Verify"
        sendOtpButton.isEnabled = true
        isOtpConfirmed = false
        generatedOtp = null
        otpContainer.visibility = View.GONE

        editContactNumberLayout.visibility = View.VISIBLE
        editContactNumber.setText(currentContact)
        contactNumberText.visibility = View.GONE

        setupDepartmentSpinner(userTypeText.text.toString(), currentDepartment)
        editDepartmentSpinner.visibility = View.VISIBLE
        departmentText.visibility = View.GONE

        sendOtpButton.visibility = View.VISIBLE
    }

    private fun saveChanges() {
        val newFull = editFullName.text.toString().trim()
        val newEmail = editEmail.text.toString().trim()
        val newContact = editContactNumber.text.toString().trim()
        val selectedDepartment = editDepartmentSpinner.selectedItem?.toString()?.trim().orEmpty()
        val otp = editOtp.text.toString().trim()
        val currentEmail = emailText.text.toString().trim()
        val currentDepartment = departmentText.text.toString().trim()
        val finalDepartment = if (selectedDepartment.isNotBlank() && selectedDepartment != "Department") {
            selectedDepartment
        } else {
            currentDepartment
        }

        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userID = sharedPreferences.getString("userID", null)

        // Validations
        if (emailEditContainer.visibility == View.VISIBLE && otp.isNotBlank()) {
            if (otp != generatedOtp) { Toast.makeText(requireContext(), "Invalid OTP", Toast.LENGTH_SHORT).show(); return }
        }
        if (newContact.isNotBlank() && newContact.length != 11) { Toast.makeText(requireContext(), "11 digits required", Toast.LENGTH_SHORT).show(); return }
        if (newEmail != currentEmail && !isOtpConfirmed) { Toast.makeText(requireContext(), "Verify new email first", Toast.LENGTH_SHORT).show(); return }
        if (finalDepartment.isBlank()) { Toast.makeText(requireContext(), "Please select a department", Toast.LENGTH_SHORT).show(); return }

        showLoadingDialog()

        lifecycleScope.launch {
            val updatedOn = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val result = withContext(Dispatchers.IO) {
                MySQLHelper.updateOfficerWithUserID(
                    newFull,
                    if (isOtpConfirmed) newEmail else currentEmail,
                    newContact,
                    userID!!,
                    updatedOn,
                    newDepartment = finalDepartment
                )
            }

            dismissLoadingDialog()
            if (result) {
                val editor = sharedPreferences.edit()
                editor.putString("fullName", newFull)
                editor.putString("email", if (isOtpConfirmed) newEmail else currentEmail)
                editor.putString("contactNumber", newContact)
                editor.putString("department", finalDepartment)
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

        editFullNameLayout.visibility = View.GONE
        editDepartmentSpinner.visibility = View.GONE
        emailEditContainer.visibility = View.GONE
        editContactNumberLayout.visibility = View.GONE
        otpContainer.visibility = View.GONE
        sendOtpButton.visibility = View.GONE
        sendOtpButton.isEnabled = true
        sendOtpButton.text = "Verify"
        editEmail.isEnabled = true
        isOtpConfirmed = false
        generatedOtp = null

        fullNameText.visibility = View.VISIBLE
        departmentText.visibility = View.VISIBLE
        emailText.visibility = View.VISIBLE
        contactNumberText.visibility = View.VISIBLE

        loadInitialData()
    }

    private fun sendOtp() {
        val newEmail = editEmail.text.toString().trim()
        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userID = sharedPreferences.getString("userID", "")!!

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
                Toast.makeText(requireContext(), "OTP sent", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmOtp() {
        if (editOtp.text.toString().trim() == generatedOtp) {
            isOtpConfirmed = true
            Toast.makeText(requireContext(), "Confirmed", Toast.LENGTH_SHORT).show()
            otpContainer.visibility = View.GONE
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
            val dbData = withContext(Dispatchers.IO) { MySQLHelper.getOfficerProfile(userID) }
            if (dbData != null && isAdded) {
                fullNameText.text = dbData["fullName"]
                emailText.text = dbData["email"]
                contactNumberText.text = dbData["contactNumber"]
                userTypeText.text = dbData["userType"]
                employeeTypeText.text = dbData["systemRole"]
                departmentText.text = dbData["department"]
                dateCreatedText.text = dbData["createdOn"]

                sharedPreferences.edit().putString("department", dbData["department"]).apply()
            }
        }
    }

    private fun setupDepartmentSpinner(userType: String, selectedDepartment: String) {
        val normalizedUserType = userType.trim().lowercase()
        val departmentArrayRes = if (normalizedUserType.contains("employee")
            || normalizedUserType.contains("officer")
            || normalizedUserType.contains("admin")
        ) {
            R.array.departments_employee
        } else {
            R.array.departments_student
        }

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_register_selected_item,
            resources.getStringArray(departmentArrayRes).toList()
        ).apply {
            setDropDownViewResource(R.layout.spinner_register_dropdown_item)
        }

        editDepartmentSpinner.adapter = adapter
        val index = adapter.getPosition(selectedDepartment).takeIf { it >= 0 } ?: 0
        editDepartmentSpinner.setSelection(index)
    }

    private fun setNonEditableFieldsDimmed(dim: Boolean) {
        val color = if (dim) Color.parseColor("#AAAAAA") else Color.parseColor("#222222")
        val alpha = if (dim) 0.6f else 1.0f
        listOf(userTypeText, employeeTypeText, dateCreatedText).forEach {
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