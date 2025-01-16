package com.capstone.navicamp

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_login, container, false)

        val usernameEditText = view.findViewById<EditText>(R.id.username)
        val passwordEditText = view.findViewById<EditText>(R.id.password)
        val loginButton = view.findViewById<Button>(R.id.login_button)
        val createAccountButton = view.findViewById<Button>(R.id.create_account_button)
        val forgotPasswordText = view.findViewById<TextView>(R.id.forgot_password)

        // Handle Login Button Click
        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (username.isNotBlank() && password.isNotBlank()) {
                // Use Coroutines for database operations
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val userType = withContext(Dispatchers.IO) {
                            MySQLHelper.validateUser(username, password)
                        }

                        if (userType != null) {
                            // Login successful, navigate to the appropriate activity
                            navigateToActivity(userType)
                        } else {
                            // Show an error if the username or password is incorrect
                            showToast("Invalid Username or Password")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast("An error occurred: ${e.message}")
                    }
                }
            } else {
                // Show a message to prompt user to enter username and password
                showToast("Please enter both Username and Password")
            }
        }

        // Handle Create Account Button Click
        createAccountButton.setOnClickListener {
            val registerBottomSheet = RegisterBottomSheet() // Create instance of RegisterBottomSheet
            registerBottomSheet.show(parentFragmentManager, registerBottomSheet.tag) // Show it
            dismiss() // Close the current LoginBottomSheet
        }

        // Handle Forgot Password Click
        forgotPasswordText.setOnClickListener {
            val dialogView = inflater.inflate(R.layout.dialog_forgot_password, null)
            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .create()
                .show()
        }

        return view
    }

    private fun navigateToActivity(userType: String) {
        when (userType) {
            "Security Officer" -> {
                val intent = Intent(activity, SecurityOfficerActivity::class.java)
                startActivity(intent)
                dismiss()
            }
            "Personnel", "Student", "Visitor" -> {
                val intent = Intent(activity, LocomotorDisabilityActivity::class.java)
                startActivity(intent)
                dismiss()
            }
            else -> {
                // Handle unknown user type (optional)
                showToast("Unknown User Type")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background =
                ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)
        }
        return dialog
    }
}
