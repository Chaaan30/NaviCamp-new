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
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.room.util.query
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog

class LoginBottomSheet : BottomSheetDialogFragment() {

    private val supabase = SupabaseClientSingleton.supabase // Using the singleton instance

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

            // Check if fields are empty
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in both fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
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
