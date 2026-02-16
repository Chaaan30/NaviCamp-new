package com.capstone.navicamp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import android.util.Log
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.Random
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import android.graphics.drawable.ColorDrawable
import android.widget.TextView

class RegisterBottomSheet : BottomSheetDialogFragment() {

    private lateinit var campusAffiliationSpinner: Spinner
    private lateinit var userTypeSpinner: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var fullNameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var contactNumberEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var otpEditText: EditText
    private lateinit var sendOtpButton: Button
    private lateinit var sendOtpProgressBar: ProgressBar
    private lateinit var otpTimerTextView: TextView
    private lateinit var termsConditionsCheckbox: CheckBox
    private lateinit var termsConditionsText: TextView
    private lateinit var termsConditionsLabel: TextView
    private var generatedOtp: String? = null
    private var isOtpSent: Boolean = false
    private var campusAffiliationSelectionTouched = false
    private var systemRoleSelectionTouched = false
    private var isUpdatingSystemRoleOptions = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_register, container, false)

        campusAffiliationSpinner = view.findViewById(R.id.campus_affiliation_spinner)
        userTypeSpinner = view.findViewById(R.id.user_type_spinner)
        fullNameEditText = view.findViewById(R.id.fullname)
        emailEditText = view.findViewById(R.id.email)
        contactNumberEditText = view.findViewById(R.id.contact_number)
        passwordEditText = view.findViewById(R.id.password)
        confirmPasswordEditText = view.findViewById(R.id.confirm_password)
        otpEditText = view.findViewById(R.id.otp)
        sendOtpButton = view.findViewById(R.id.send_otp)
        sendOtpProgressBar = view.findViewById(R.id.send_otp_progress_bar)
        otpTimerTextView = view.findViewById(R.id.otp_timer)
        termsConditionsCheckbox = view.findViewById(R.id.terms_conditions_checkbox)
        termsConditionsText = view.findViewById(R.id.terms_conditions_text)
        termsConditionsLabel = view.findViewById(R.id.terms_conditions_label)

        campusAffiliationSpinner.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                campusAffiliationSelectionTouched = true
            }
            false
        }

        userTypeSpinner.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                systemRoleSelectionTouched = true
            }
            false
        }


        // Set input filter for contact number to accept only 11 digits
        contactNumberEditText.filters =
            arrayOf(InputFilter.LengthFilter(11), InputFilter { source, _, _, dest, _, _ ->
                if (source.isEmpty()) return@InputFilter null // Allow deletion
                if (source.length + dest.length > 11) return@InputFilter "" // Restrict to 11 digits
                if (!source.matches(Regex("\\d+"))) return@InputFilter "" // Allow only digits
                null
            })

        setupCampusAffiliationSpinner()
        updateSystemRoleOptions("Campus Affiliation")

        sendOtpButton.setOnClickListener {
            val email = emailEditText.text.toString()
            if (email.isNotEmpty()) {
                sendOtpButton.visibility = View.GONE
                sendOtpProgressBar.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    generatedOtp = generateOtp()
                    sendOtpEmail(email, generatedOtp!!)
                    sendOtpProgressBar.visibility = View.GONE
                    otpEditText.visibility = View.VISIBLE
                    startOtpTimer()
                    isOtpSent = true
                }
            } else {
                Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
            }
        }

        val loginButton = view.findViewById<Button>(R.id.login)
        loginButton.setOnClickListener {
            val loginBottomSheet = LoginBottomSheet()
            loginBottomSheet.show(parentFragmentManager, "LoginBottomSheet")
            dismiss()
        }

        val createAccountButton = view.findViewById<Button>(R.id.create_account)
        createAccountButton.setOnClickListener {
            registerUser(view)
        }

        termsConditionsText.setOnClickListener {
            val dialogView = inflater.inflate(R.layout.dialog_terms_conditions, null)
            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .show()
        }

        return view
    }

    private fun startOtpTimer() {
        sendOtpButton.visibility = View.GONE
        otpTimerTextView.visibility = View.VISIBLE
        object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                otpTimerTextView.text = "Resend in\n${millisUntilFinished / 1000}"
            }

            override fun onFinish() {
                otpTimerTextView.visibility = View.GONE
                sendOtpButton.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun setupCampusAffiliationSpinner() {
        campusAffiliationSpinner.adapter = createRegisterSpinnerAdapter(R.array.campus_affiliations)

        campusAffiliationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedAffiliation = parent.getItemAtPosition(position).toString()
                updateSystemRoleOptions(selectedAffiliation)
                if (campusAffiliationSelectionTouched && position == 0) {
                    Toast.makeText(
                        context,
                        "Please select from the Campus Affiliation choices",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                updateSystemRoleOptions("Campus Affiliation")
            }
        }
    }

    private fun updateSystemRoleOptions(campusAffiliation: String) {
        val roleArrayRes = if (campusAffiliation == "Employee") {
            R.array.system_roles_employee
        } else {
            R.array.system_roles_student
        }

        isUpdatingSystemRoleOptions = true
        userTypeSpinner.adapter = createRegisterSpinnerAdapter(roleArrayRes)
        userTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (isUpdatingSystemRoleOptions) {
                    isUpdatingSystemRoleOptions = false
                    return
                }
                if (systemRoleSelectionTouched && position == 0) {
                    Toast.makeText(
                        context,
                        "Please select from the System Role choices",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun createRegisterSpinnerAdapter(arrayResId: Int): ArrayAdapter<String> {
        val items = resources.getStringArray(arrayResId).toList()
        return ArrayAdapter(requireContext(), R.layout.spinner_register_selected_item, items).apply {
            setDropDownViewResource(R.layout.spinner_register_dropdown_item)
        }
    }

    private fun registerUser(view: View) {
        val fullName = fullNameEditText.text.toString()
        val email = emailEditText.text.toString()
        val contactNumber = contactNumberEditText.text.toString()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val otp = otpEditText.text.toString()
        val campusAffiliation = campusAffiliationSpinner.selectedItem.toString()
        val systemRole = userTypeSpinner.selectedItem.toString()

        // Validation
        if (fullName.isEmpty() || email.isEmpty() || contactNumber.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || otp.isEmpty()) {
            Toast.makeText(context, "Please fill all the fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (campusAffiliation == "Campus Affiliation") {
            Toast.makeText(context, "Please select a campus affiliation", Toast.LENGTH_SHORT).show()
            return
        }

        if (systemRole == "System Role") {
            Toast.makeText(context, "Please select a valid system role", Toast.LENGTH_SHORT).show()
            return
        }

        if (!termsConditionsCheckbox.isChecked) {
            Toast.makeText(
                context,
                "Please read and agree to the Terms and Conditions",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isOtpSent) {
            Toast.makeText(context, "Please send the OTP", Toast.LENGTH_SHORT).show()
            return
        }

        if (otp != generatedOtp) {
            Toast.makeText(context, "Invalid OTP", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 7) {
            Toast.makeText(
                context,
                "Password must be at least 7 characters long",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        if (contactNumber.length != 11) {
            Toast.makeText(context, "Contact number must be exactly 11 digits", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Show loading dialog
        val loadingDialog = showLoadingDialog()

        // Hash the password
        val hashedPassword = hashPassword(password)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Check if email already exists BEFORE uploading image or inserting user
                val emailExists = withContext(Dispatchers.IO) {
                    MySQLHelper.fetchUserEmailByEmail(email) != null
                }
                if (emailExists) {
                    loadingDialog.dismiss()
                    Toast.makeText(context, "Email already exists", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val userID: String? = withContext(Dispatchers.IO) {
                    MySQLHelper.generateUserID()
                }
                Log.d("RegisterBottomSheet", "Generated userID: $userID")
                
                if (userID == null) {
                    loadingDialog.dismiss()
                    Toast.makeText(context, "Failed to generate User ID. Please try again.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val isInserted = withContext(Dispatchers.IO) {
                    MySQLHelper.insertUserWithPwdProfile(
                        userID,
                        fullName,
                        campusAffiliation,
                        email,
                        contactNumber,
                        hashedPassword,
                        systemRole
                    )
                }
                loadingDialog.dismiss()
                if (isInserted) {
                    Toast.makeText(context, "User registered successfully", Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    Toast.makeText(context, "Registration failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Log.e("RegisterUser", "Registration or S3/Email failed", e)
                Toast.makeText(context, "Registration process failed. Error: "+e.localizedMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashedBytes = md.digest(password.toByteArray())
        return hashedBytes.joinToString("") { "%02x".format(it) }
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
            } catch (e: Exception) {
                e.printStackTrace()
                // Inform the user that OTP delivery failed.
                CoroutineScope(Dispatchers.Main).launch {
                     Toast.makeText(context, "Failed to send OTP email. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun showLoadingDialog(): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_loading)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        return dialog
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            bottomSheet?.background =
                ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)
        }
        return dialog
    }
}
