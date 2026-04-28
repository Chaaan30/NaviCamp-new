package com.capstone.navicamp

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Properties
import java.util.Random
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class ForgotPasswordBottomSheet : BottomSheetDialogFragment() {

    private lateinit var emailEditText: EditText
    private lateinit var otpEditText: EditText
    private lateinit var newPasswordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var sendOtpButton: Button
    private lateinit var otpTimerTextView: TextView
    private lateinit var confirmOtpButton: Button
    private lateinit var resetPasswordButton: Button

    private lateinit var requirementLengthTextView: TextView
    private lateinit var requirementCaseTextView: TextView
    private lateinit var requirementNumberTextView: TextView
    private lateinit var requirementSymbolTextView: TextView

    private var generatedOtp: String? = null
    private var isOtpConfirmed: Boolean = false

    private data class PasswordRuleResult(
        val score: Int,
        val failedRequirements: List<String>
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_forgot_password, container, false)

        emailEditText = view.findViewById(R.id.email)
        otpEditText = view.findViewById(R.id.otp)
        newPasswordEditText = view.findViewById(R.id.new_password)
        confirmPasswordEditText = view.findViewById(R.id.confirm_password)
        sendOtpButton = view.findViewById(R.id.send_otp_button)
        otpTimerTextView = view.findViewById(R.id.otp_timer)
        confirmOtpButton = view.findViewById(R.id.confirm_otp_button)
        resetPasswordButton = view.findViewById(R.id.reset_password_button)

        requirementLengthTextView = view.findViewById(R.id.password_requirement_length)
        requirementCaseTextView = view.findViewById(R.id.password_requirement_case)
        requirementNumberTextView = view.findViewById(R.id.password_requirement_number)
        requirementSymbolTextView = view.findViewById(R.id.password_requirement_symbol)

        setupPasswordRequirementWatcher()

        sendOtpButton.setOnClickListener { onSendOtpButtonClick() }
        confirmOtpButton.setOnClickListener { onConfirmOtpButtonClick() }
        resetPasswordButton.setOnClickListener { onResetPasswordButtonClick() }

        return view
    }

    private fun onSendOtpButtonClick() {
        val email = emailEditText.text.toString()
        if (email.isNotEmpty()) {
            val loadingDialog = showLoadingDialog()
            CoroutineScope(Dispatchers.Main).launch {
                val userEmail = withContext(Dispatchers.IO) {
                    MySQLHelper.fetchUserEmailByEmail(email)
                }
                if (userEmail != null) {
                    generatedOtp = generateOtp()
                    withContext(Dispatchers.IO) {
                        sendOtpEmail(userEmail, generatedOtp!!)
                    }
                    loadingDialog.dismiss()
                    view?.findViewById<View>(R.id.otp_layout)?.visibility = View.VISIBLE
                    otpEditText.visibility = View.VISIBLE
                    confirmOtpButton.visibility = View.VISIBLE
                    startOtpTimer()
                    Toast.makeText(context, "OTP sent to email", Toast.LENGTH_SHORT).show()
                } else {
                    loadingDialog.dismiss()
                    emailEditText.visibility = View.VISIBLE
                    sendOtpButton.visibility = View.VISIBLE
                    Toast.makeText(context, "Email not found", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onConfirmOtpButtonClick() {
        val otp = otpEditText.text.toString()
        if (otp == generatedOtp) {
            isOtpConfirmed = true
            view?.findViewById<View>(R.id.new_password_layout)?.visibility = View.VISIBLE
            view?.findViewById<View>(R.id.confirm_password_layout)?.visibility = View.VISIBLE
            view?.findViewById<View>(R.id.password_requirements_card)?.visibility = View.VISIBLE
            newPasswordEditText.visibility = View.VISIBLE
            confirmPasswordEditText.visibility = View.VISIBLE
            resetPasswordButton.visibility = View.VISIBLE
            confirmOtpButton.visibility = View.GONE
            sendOtpButton.visibility = View.GONE
            view?.findViewById<View>(R.id.otp_layout)?.visibility = View.GONE
            otpEditText.visibility = View.GONE
            otpTimerTextView.visibility = View.GONE
            Toast.makeText(context, "OTP confirmed", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Invalid OTP", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onResetPasswordButtonClick() {
        val newPassword = newPasswordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        val passwordRuleResult = validatePasswordRequirements(newPassword)
        if (passwordRuleResult.score < 3) {
            val failedRulesText = passwordRuleResult.failedRequirements.joinToString("\n- ")
            Toast.makeText(context, "Password must meet at least 3 requirements:\n- $failedRulesText", Toast.LENGTH_LONG).show()
            return
        }

        if (newPassword == confirmPassword) {
            val loadingDialog = showLoadingDialog()
            CoroutineScope(Dispatchers.Main).launch {
                val hashedPassword = hashPassword(newPassword)
                val email = emailEditText.text.toString()
                val result = withContext(Dispatchers.IO) {
                    MySQLHelper.updateUserPasswordByEmail(email, hashedPassword)
                }
                loadingDialog.dismiss()
                if (result) {
                    Toast.makeText(context, "Password reset successfully", Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    Toast.makeText(context, "Failed to reset password", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startOtpTimer() {
        sendOtpButton.visibility = View.GONE
        otpTimerTextView.visibility = View.VISIBLE
        object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                otpTimerTextView.text = "Resend OTP in ${millisUntilFinished / 1000} seconds"
            }

            override fun onFinish() {
                otpTimerTextView.visibility = View.GONE
                if (!isOtpConfirmed) {
                    sendOtpButton.text = "Resend OTP"
                    sendOtpButton.visibility = View.VISIBLE
                    confirmOtpButton.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        sendOtpButton.text = "Send OTP"
        isOtpConfirmed = false
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

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashedBytes = md.digest(password.toByteArray())
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }

    private fun setupPasswordRequirementWatcher() {
        updatePasswordRequirementIndicators("")
        newPasswordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordRequirementIndicators(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun updatePasswordRequirementIndicators(password: String) {
        val hasValidLength = password.length in 12..16
        val hasUppercaseAndLowercase =
            password.any { it.isUpperCase() } && password.any { it.isLowerCase() }
        val hasNumber = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        setRequirementColor(requirementLengthTextView, hasValidLength)
        setRequirementColor(requirementCaseTextView, hasUppercaseAndLowercase)
        setRequirementColor(requirementNumberTextView, hasNumber)
        setRequirementColor(requirementSymbolTextView, hasSymbol)
    }

    private fun setRequirementColor(textView: TextView, isMet: Boolean) {
        if (context != null) {
            val colorRes = if (isMet) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            textView.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        }
    }

    private fun validatePasswordRequirements(password: String): PasswordRuleResult {
        val hasValidLength = password.length in 12..16
        val hasUppercaseAndLowercase =
            password.any { it.isUpperCase() } && password.any { it.isLowerCase() }
        val hasNumber = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        val checks = listOf(
            hasValidLength to "12-16 characters",
            hasUppercaseAndLowercase to "both uppercase and lowercase letters",
            hasNumber to "at least 1 number",
            hasSymbol to "at least 1 symbol"
        )

        return PasswordRuleResult(
            score = checks.count { it.first },
            failedRequirements = checks.filterNot { it.first }.map { it.second }
        )
    }

    private fun showLoadingDialog(): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_loading)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        return dialog
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