package com.capstone.navicamp

import android.app.Dialog
import android.content.Intent
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
import android.content.Context
import android.util.Log
import android.graphics.drawable.ColorDrawable

class LoginBottomSheet : BottomSheetDialogFragment() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var forgotPasswordTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_login, container, false)

        emailEditText = view.findViewById(R.id.email)
        passwordEditText = view.findViewById(R.id.password)
        forgotPasswordTextView = view.findViewById(R.id.forgot_password) // Initialize forgotPasswordTextView

        val loginButton = view.findViewById<Button>(R.id.login_button)
        val createAccountButton = view.findViewById<Button>(R.id.create_account_button)

        forgotPasswordTextView.setOnClickListener {
            val forgotPasswordBottomSheet = ForgotPasswordBottomSheet()
            forgotPasswordBottomSheet.show(parentFragmentManager, forgotPasswordBottomSheet.tag)
            dismiss() // Hide the login bottom sheet
        }

        loginButton.setOnClickListener {
            loginUser(view)
        }

        createAccountButton.setOnClickListener {
            val registerBottomSheet = RegisterBottomSheet()
            registerBottomSheet.show(parentFragmentManager, registerBottomSheet.tag)
            dismiss()
        }

        return view
    }

    private fun showLoadingDialog(): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_loading)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        return dialog
    }

    private fun loginUser(view: View) {
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()

        // Validation
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading dialog
        val loadingDialog = showLoadingDialog()

        // Handle login logic
        CoroutineScope(Dispatchers.Main).launch {
            val startTime = System.currentTimeMillis()
            try {
                val userData = withContext(Dispatchers.IO) {
                    MySQLHelper.loginUser(email, password)
                }

                if (userData != null) {
                    UserSingleton.fullName = userData.fullName // Set the fullName in UserSingleton

                    val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                    with(sharedPreferences.edit()) {
                        putString("userID", userData.userID)
                        putString("fullName", userData.fullName)
                        putString("userType", userData.userType)
                        putString("email", userData.email)
                        putString("contactNumber", userData.contactNumber)
                        putString("createdOn", userData.createdOn)
                        putString("updatedOn", userData.updatedOn)
                        putBoolean("isLoggedIn", true) // Store login state
                        apply()
                    }

                    val intent = when (userData.userType) {
                        "Student", "Personnel", "Visitor" -> Intent(context, LocomotorDisabilityActivity::class.java)
                        "Security Officer" -> Intent(context, SecurityOfficerActivity::class.java)
                        else -> null
                    }
                    intent?.let {
                        startActivity(it)
                        dismiss()
                    }
                } else {
                    Toast.makeText(context, "Invalid email or password", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("LoginBottomSheet", "Error during login", e)
                Toast.makeText(context, "An error occurred during login", Toast.LENGTH_SHORT).show()
            } finally {
                // Dismiss loading dialog
                loadingDialog.dismiss()

                // Check if the internet is slow
                val endTime = System.currentTimeMillis()
                if (endTime - startTime > 5000) { // 5 seconds threshold for slow internet
                    Toast.makeText(context, "Internet is slow, please try again later", Toast.LENGTH_SHORT).show()
                }
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