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

class RegisterBottomSheet : BottomSheetDialogFragment() {

    private lateinit var userTypeSpinner: Spinner

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_register, container, false)

        userTypeSpinner = view.findViewById(R.id.user_type_spinner)
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.user_types_reg,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            userTypeSpinner.adapter = adapter
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
        val email = view.findViewById<EditText>(R.id.email).text.toString()
        val contactNumber = view.findViewById<EditText>(R.id.contact_number).text.toString()
        val password = view.findViewById<EditText>(R.id.password).text.toString()
        val userType = userTypeSpinner.selectedItem.toString()
        val termsConditionsCheckbox = view.findViewById<CheckBox>(R.id.terms_conditions_checkbox)

        // Validation
        if (fullName.isEmpty() || email.isEmpty() || contactNumber.isEmpty() || password.isEmpty()) {
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