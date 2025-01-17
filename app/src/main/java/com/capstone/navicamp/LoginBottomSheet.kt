package com.capstone.navicamp

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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

        val loginButton = view.findViewById<Button>(R.id.login_button)
        val createAccountButton = view.findViewById<Button>(R.id.create_account_button)

        // Handle Login Button Click
        loginButton.setOnClickListener {
            loginUser(view)
        }

        // Handle Create Account Button Click
        createAccountButton.setOnClickListener {
            val registerBottomSheet = RegisterBottomSheet() // Create instance of RegisterBottomSheet
            registerBottomSheet.show(parentFragmentManager, registerBottomSheet.tag) // Show it
            dismiss() // Close the current LoginBottomSheet
        }

        return view
    }

    private fun loginUser(view: View) {
        val accessCode = view.findViewById<EditText>(R.id.logged_access_code).text.toString()

        // Validation
        if (accessCode.isEmpty()) {
            Toast.makeText(context, "Please enter the access code", Toast.LENGTH_SHORT).show()
            return
        }

        // Check access code and navigate based on user type
        CoroutineScope(Dispatchers.Main).launch {
            val userType = withContext(Dispatchers.IO) {
                MySQLHelper.getUserTypeIfFullNameExists(accessCode)
            }

            if (userType != null) {
                navigateToActivity(userType)
            } else {
                Toast.makeText(view.context, "Invalid Access Code or Full Name not set", Toast.LENGTH_SHORT).show()
            }
        }
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