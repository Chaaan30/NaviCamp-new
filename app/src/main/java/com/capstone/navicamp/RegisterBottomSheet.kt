package com.capstone.navicamp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
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
import org.mindrot.jbcrypt.BCrypt
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
    private lateinit var otpEditText: EditText
    private lateinit var sendOtpButton: Button
    private lateinit var termsConditionsCheckbox: CheckBox
    private lateinit var termsConditionsText: TextView
    private var generatedOtp: String? = null

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
        otpEditText = view.findViewById(R.id.otp)
        sendOtpButton = view.findViewById(R.id.send_otp)
        termsConditionsCheckbox = view.findViewById(R.id.terms_conditions_checkbox)
        termsConditionsText = view.findViewById(R.id.terms_conditions_text)

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
                generatedOtp = generateOtp()
                CoroutineScope(Dispatchers.Main).launch {
                    sendOtpEmail(email, generatedOtp!!)
                }
                otpEditText.visibility = View.VISIBLE
                Toast.makeText(context, "OTP sent to email", Toast.LENGTH_SHORT).show()
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
                .create()
                .show()
        }

        return view
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

    private fun registerUser(view: View) {
        val fullName = fullNameEditText.text.toString()
        val email = emailEditText.text.toString()
        val contactNumber = contactNumberEditText.text.toString()
        val password = passwordEditText.text.toString()
        val otp = otpEditText.text.toString()
        val userType = userTypeSpinner.selectedItem.toString()

        // Validation
        if (fullName.isEmpty() || email.isEmpty() || contactNumber.isEmpty() || password.isEmpty() || otp.isEmpty()) {
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

        if (otp != generatedOtp) {
            Toast.makeText(context, "Invalid OTP", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress bar and hide input fields
        progressBar.visibility = View.VISIBLE
        fullNameEditText.visibility = View.GONE
        emailEditText.visibility = View.GONE
        contactNumberEditText.visibility = View.GONE
        passwordEditText.visibility = View.GONE
        otpEditText.visibility = View.GONE
        userTypeSpinner.visibility = View.GONE
        termsConditionsCheckbox.visibility = View.GONE
        termsConditionsText.visibility = View.GONE

        // Hash the password
        val hashedPassword = hashPassword(password)

        // Register user
        CoroutineScope(Dispatchers.Main).launch {
            val userID = withContext(Dispatchers.IO) {
                MySQLHelper.generateUserID()
            }

            if (userID != null) {
                val result = withContext(Dispatchers.IO) {
                    MySQLHelper.insertUser(userID, fullName, userType, email, contactNumber, hashedPassword)
                }
                if (result) {
                    Toast.makeText(view.context, "Registration Successful!", Toast.LENGTH_SHORT).show()
                    dismiss()
                    val loginBottomSheet = LoginBottomSheet()
                    loginBottomSheet.show(parentFragmentManager, "LoginBottomSheet")
                } else {
                    Toast.makeText(view.context, "Failed to register user", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(view.context, "Failed to generate userID", Toast.LENGTH_SHORT).show()
            }

            // Hide progress bar and show input fields
            progressBar.visibility = View.GONE
            fullNameEditText.visibility = View.VISIBLE
            emailEditText.visibility = View.VISIBLE
            contactNumberEditText.visibility = View.VISIBLE
            passwordEditText.visibility = View.VISIBLE
            otpEditText.visibility = View.VISIBLE
            userTypeSpinner.visibility = View.VISIBLE
            termsConditionsCheckbox.visibility = View.VISIBLE
            termsConditionsText.visibility = View.VISIBLE
        }
    }

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashedBytes = md.digest(password.toByteArray())
        return hashedBytes.joinToString("") { "%02x".format(it) }
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