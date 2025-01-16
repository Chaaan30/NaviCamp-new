package com.capstone.navicamp

import android.app.AlertDialog
import android.app.Dialog
import android.os.AsyncTask
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog

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

        val termsConditionsCheckBox = view.findViewById<CheckBox>(R.id.terms_conditions_checkbox)
        val termsConditionsText = view.findViewById<TextView>(R.id.terms_conditions_text)

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

        val createAccountButton = view.findViewById<Button>(R.id.create_account)
        createAccountButton.setOnClickListener {
            registerUser(view)
        }

        return view
    }

    private fun registerUser(view: View) {
        val fullName = view.findViewById<EditText>(R.id.fullname).text.toString()
        val userName = view.findViewById<EditText>(R.id.username).text.toString()
        val contactNumber = view.findViewById<EditText>(R.id.contact_number).text.toString()
        val email = view.findViewById<EditText>(R.id.email).text.toString()
        val password = view.findViewById<EditText>(R.id.password).text.toString()
        val confirmPassword = view.findViewById<EditText>(R.id.confirm_password).text.toString()
        val userType = view.findViewById<Spinner>(R.id.user_type_spinner).selectedItem.toString()
        val termsAccepted = view.findViewById<CheckBox>(R.id.terms_conditions_checkbox).isChecked

        // Validation
        if (fullName.isEmpty() || userName.isEmpty() || contactNumber.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(context, "Please fill all the fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (userType == "User Types") {
            Toast.makeText(context, "Please select a user type", Toast.LENGTH_SHORT).show()
            return
        }

        if (!termsAccepted) {
            Toast.makeText(context, "Please read and accept the Terms and Conditions", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure password and confirm password match
        if (password != confirmPassword) {
            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        // Execute background task to insert user data into MySQL
        InsertUserTask(view, this).execute(fullName, userName, password, userType, email, contactNumber)
    }

    private class InsertUserTask(val view: View, val fragment: RegisterBottomSheet) : AsyncTask<String, Void, Boolean>() {
        override fun doInBackground(vararg params: String?): Boolean {
            val fullName = params[0] ?: ""
            val userName = params[1] ?: ""
            val password = params[2] ?: ""
            val userType = params[3] ?: ""
            val email = params[4] ?: ""
            val contactNumber = params[5] ?: ""

            // Call the insertUser function from MySQLHelper
            return MySQLHelper.insertUser(
                fullName = fullName,
                userName = userName,
                password = password,
                userType = userType,
                email = email,
                contactNumber = contactNumber
            )
        }

        override fun onPostExecute(result: Boolean) {
            if (result) {
                Toast.makeText(view.context, "Registration Successful!", Toast.LENGTH_SHORT).show()
                fragment.dismiss()
                val loginBottomSheet = LoginBottomSheet()
                loginBottomSheet.show(fragment.parentFragmentManager, "LoginBottomSheet")
            } else {
                Toast.makeText(view.context, "Registration Failed!", Toast.LENGTH_SHORT).show()
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