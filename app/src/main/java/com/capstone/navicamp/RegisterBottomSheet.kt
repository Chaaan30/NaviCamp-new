package com.capstone.navicamp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterBottomSheet : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_register, container, false)

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

        val termsConditionsText = view.findViewById<TextView>(R.id.terms_conditions_text)
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

    private fun registerUser(view: View) {
        val fullName = view.findViewById<EditText>(R.id.fullname).text.toString()
        val accessCode = view.findViewById<EditText>(R.id.access_code).text.toString()
        val termsConditionsCheckbox = view.findViewById<CheckBox>(R.id.terms_conditions_checkbox)

        // Validation
        if (fullName.isEmpty() || accessCode.isEmpty()) {
            Toast.makeText(context, "Please fill all the fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!termsConditionsCheckbox.isChecked) {
            Toast.makeText(context, "Please read and agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
            return
        }

        // Check access code and update user
        CoroutineScope(Dispatchers.Main).launch {
            val existingFullName = withContext(Dispatchers.IO) {
                MySQLHelper.getFullNameByAccessCode(accessCode)
            }

            if (existingFullName != null && existingFullName.isNotEmpty()) {
                Toast.makeText(view.context, "Access Code is already taken", Toast.LENGTH_SHORT).show()
            } else {
                val result = withContext(Dispatchers.IO) {
                    MySQLHelper.updateUser(accessCode, fullName)
                }
                if (result) {
                    Toast.makeText(view.context, "Registration Successful!", Toast.LENGTH_SHORT).show()
                    dismiss()
                    val loginBottomSheet = LoginBottomSheet()
                    loginBottomSheet.show(parentFragmentManager, "LoginBottomSheet")
                } else {
                    Toast.makeText(view.context, "Failed to register user", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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