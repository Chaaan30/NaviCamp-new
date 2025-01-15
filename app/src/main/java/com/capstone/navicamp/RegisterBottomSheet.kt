package com.capstone.navicamp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.mindrot.jbcrypt.BCrypt

val supabase = SupabaseClientSingleton.supabase

class RegisterBottomSheet : BottomSheetDialogFragment() {

    private lateinit var registerView: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        registerView = inflater.inflate(R.layout.bottom_sheet_register, container, false)

        val fullnameInput = registerView.findViewById<EditText>(R.id.fullname)
        val usernameInput = registerView.findViewById<EditText>(R.id.username)
        val contactNumberInput = registerView.findViewById<EditText>(R.id.contact_number)
        val emailInput = registerView.findViewById<EditText>(R.id.email)
        val passwordInput = registerView.findViewById<EditText>(R.id.password)
        val confirmPasswordInput = registerView.findViewById<EditText>(R.id.confirm_password)
        val userTypeSpinner = registerView.findViewById<Spinner>(R.id.user_type_spinner)
        val registerButton = registerView.findViewById<Button>(R.id.create_account)
        val termsConditionsCheckBox = registerView.findViewById<CheckBox>(R.id.terms_conditions_checkbox)
        val termsConditionsText = registerView.findViewById<TextView>(R.id.terms_conditions_text)
        val loginButton = registerView.findViewById<Button>(R.id.login)


        // Set up Terms and Conditions text with clickable span
        val fullText = "I agree to the Terms and Conditions"
        val spannableString = SpannableString(fullText)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.custom_blue)
        spannableString.setSpan(
            ForegroundColorSpan(blueColor),
            fullText.indexOf("Terms and Conditions"),
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        termsConditionsText.text = spannableString
        termsConditionsText.setOnClickListener {
            val dialogView = inflater.inflate(R.layout.dialog_terms_conditions, null)
            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .create()
                .show()
        }

        registerButton.setOnClickListener {
    val fullname = fullnameInput.text.toString()
    val username = usernameInput.text.toString()
    val contactNumberStr = contactNumberInput.text.toString()
    val contactNumber = if (contactNumberStr.isNotBlank() && contactNumberStr.all { it.isDigit() }) contactNumberStr.toLongOrNull() else null
    val email = emailInput.text.toString()
    val password = passwordInput.text.toString()
    val confirmPassword = confirmPasswordInput.text.toString()
    val userType = userTypeSpinner.selectedItem.toString()

    if (userType == "User Type") {
        Toast.makeText(context, "Please choose a user type", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
    }

    if (fullname.isBlank() || username.isBlank() || contactNumberStr.isBlank() || contactNumber == null ||
        email.isBlank() || password.isBlank() || confirmPassword.isBlank()
    ) {
        Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
    }

    if (!termsConditionsCheckBox.isChecked) {
        Toast.makeText(context, "You must agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
    }

    if (password != confirmPassword) {
        Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
    }

    // Insert data into Supabase
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Format the current time as a string
            val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
            val currentTimeString = LocalDateTime.now().format(formatter)

            // Hash the password using bcrypt
            val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

            // Use the hashed password in your User data
            val user = User(
                userName = username,
                userType = userType,
                fullName = fullname,
                email = email,
                contactNumber = contactNumber, // Use parsed Long
                password = hashedPassword,  // Store hashed password
                createdOn = currentTimeString,
                updatedOn = currentTimeString
            )

            // Insert into Supabase
            val result = supabase.postgrest["user_table"].insert(user)

            // Update UI on main thread
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Register successful", Toast.LENGTH_SHORT).show()
                dismiss() // Close the BottomSheet
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Error registering user: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

        // Switch to Login screen when "I already have an account" is clicked
        loginButton.setOnClickListener {
            // You can switch to the Login BottomSheet like this:
            switchToLoginBottomSheet()
        }

        return registerView
    }


    private fun switchToLoginBottomSheet() {
        val loginBottomSheet = LoginBottomSheet() // Assuming you have a LoginBottomSheet class for login
        loginBottomSheet.show(parentFragmentManager, loginBottomSheet.tag)
        dismiss() // Dismiss current register bottom sheet
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background =
                ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)
        }
        return dialog
    }
}


