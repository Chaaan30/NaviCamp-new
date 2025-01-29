package com.capstone.navicamp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Random
import java.util.Properties
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class RegisterBottomSheet : BottomSheetDialogFragment() {

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_register, container, false)

        userTypeSpinner = view.findViewById(R.id.user_type_spinner)
        progressBar = view.findViewById(R.id.progress_bar)
        fullNameEditText = view.findViewById(R.id.fullname)
        emailEditText = view.findViewById(R.id.email)
        contactNumberEditText = view.findViewById(R.id.contact_number)
        passwordEditText = view.findViewById(R.id.password)
        confirmPasswordEditText = view.findViewById(R.id.confirm_password)
        otpEditText = view.findViewById(R.id.otp)
        sendOtpButton = view.findViewById(R.id.send_otp)
        sendOtpProgressBar = view.findViewById(R.id.send_otp_progress_bar) // Add this line
        otpTimerTextView = view.findViewById(R.id.otp_timer)
        termsConditionsCheckbox = view.findViewById(R.id.terms_conditions_checkbox)
        termsConditionsText = view.findViewById(R.id.terms_conditions_text)
        termsConditionsLabel = view.findViewById(R.id.terms_conditions_label)

        // Set input filter for contact number to accept only 11 digits
        contactNumberEditText.filters = arrayOf(InputFilter.LengthFilter(11), InputFilter { source, start, end, dest, dstart, dend ->
            if (source.isEmpty()) return@InputFilter null // Allow deletion
            if (source.length + dest.length > 11) return@InputFilter "" // Restrict to 11 digits
            if (!source.matches(Regex("\\d+"))) return@InputFilter "" // Allow only digits
            null
        })

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.user_types_reg,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            userTypeSpinner.adapter = adapter
        }

        sendOtpButton.setOnClickListener {
            val email = emailEditText.text.toString()
            if (email.isNotEmpty()) {
                sendOtpProgressBar.visibility = View.VISIBLE
                sendOtpButton.visibility = View.GONE

                CoroutineScope(Dispatchers.Main).launch {
                    generatedOtp = generateOtp()
                    withContext(Dispatchers.IO) {
                        sendOtpEmail(email, generatedOtp!!)
                    }
                    sendOtpProgressBar.visibility = View.GONE
                    sendOtpButton.visibility = View.VISIBLE
                    otpEditText.visibility = View.VISIBLE
                    isOtpSent = true // Set isOtpSent to true
                    startOtpTimer()
                    Toast.makeText(context, "OTP sent to email", Toast.LENGTH_SHORT).show()
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

    private fun registerUser(view: View) {
        val fullName = fullNameEditText.text.toString()
        val email = emailEditText.text.toString()
        val contactNumber = contactNumberEditText.text.toString()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val otp = otpEditText.text.toString()
        val userType = userTypeSpinner.selectedItem.toString()

        // Validation
        if (fullName.isEmpty() || email.isEmpty() || contactNumber.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || otp.isEmpty()) {
            Toast.makeText(context, "Please fill all the fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (userType == "User Types") {
            Toast.makeText(context, "Please select a valid user type", Toast.LENGTH_SHORT).show()
            return
        }

        if (!termsConditionsCheckbox.isChecked) {
            Toast.makeText(context, "Please read and agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Password must be at least 7 characters long", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        if (contactNumber.length != 11) {
            Toast.makeText(context, "Contact number must be exactly 11 digits", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress bar and hide input fields
        progressBar.visibility = View.VISIBLE
        fullNameEditText.visibility = View.GONE
        emailEditText.visibility = View.GONE
        contactNumberEditText.visibility = View.GONE
        passwordEditText.visibility = View.GONE
        confirmPasswordEditText.visibility = View.GONE
        otpEditText.visibility = View.GONE
        userTypeSpinner.visibility = View.GONE
        termsConditionsCheckbox.visibility = View.GONE
        termsConditionsText.visibility = View.GONE
        termsConditionsLabel.visibility = View.GONE
        otpTimerTextView.visibility = View.GONE // Hide the timer
        sendOtpButton.visibility = View.GONE // Hide the Send OTP button
        sendOtpProgressBar.visibility = View.GONE // Hide the Send OTP progress bar

        // Hash the password
        val hashedPassword = hashPassword(password)

        // Register user
        CoroutineScope(Dispatchers.Main).launch {
            val userID = withContext(Dispatchers.IO) {
                MySQLHelper.generateUserID()
            }

            if (userID != null) {
                val isInserted = withContext(Dispatchers.IO) {
                    MySQLHelper.insertUser(userID, fullName, userType, email, contactNumber, hashedPassword)
                }

                if (isInserted) {
                    Toast.makeText(context, "User registered successfully", Toast.LENGTH_SHORT).show()
                    val loginBottomSheet = LoginBottomSheet()
                    loginBottomSheet.show(parentFragmentManager, "LoginBottomSheet")
                    dismiss()
                } else {
                    Toast.makeText(context, "Failed to register user", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Failed to generate user ID", Toast.LENGTH_SHORT).show()
            }

            // Hide progress bar and show input fields
            progressBar.visibility = View.GONE
            fullNameEditText.visibility = View.VISIBLE
            emailEditText.visibility = View.VISIBLE
            contactNumberEditText.visibility = View.VISIBLE
            passwordEditText.visibility = View.VISIBLE
            confirmPasswordEditText.visibility = View.VISIBLE
            otpEditText.visibility = View.VISIBLE
            userTypeSpinner.visibility = View.VISIBLE
            termsConditionsCheckbox.visibility = View.VISIBLE
            termsConditionsText.visibility = View.VISIBLE
            termsConditionsLabel.visibility = View.VISIBLE
            sendOtpButton.visibility = View.VISIBLE // Show the Send OTP button
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
                println("OTP email sent successfully")

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)
        }
        return dialog
    }
}