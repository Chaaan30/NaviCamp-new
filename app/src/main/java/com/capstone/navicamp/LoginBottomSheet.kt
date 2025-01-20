package com.capstone.navicamp

import android.app.Dialog
import android.content.Context.MODE_PRIVATE
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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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
                val fullName = withContext(Dispatchers.IO) {
                    MySQLHelper.getFullNameByAccessCode(accessCode)
                }
                val userID = withContext(Dispatchers.IO) {
                    MySQLHelper.getUserIDByAccessCode(accessCode)
                }
                val dateCreated = withContext(Dispatchers.IO) {
                    MySQLHelper.getUserCreationDate(userID!!)
                }
                UserSingleton.fullName = fullName

                // Generate a token
                val token = UUID.randomUUID().toString()

                // Save fullName, userID, userType, dateCreated, and token in SharedPreferences
                val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putString("fullName", fullName)
                editor.putString("userID", userID)
                editor.putString("userType", userType)
                editor.putString("dateCreated", dateCreated)
                editor.putString("token", token)
                editor.apply()

                updateUIWithFullName(fullName)
                navigateToActivity(userType)
            } else {
                Toast.makeText(view.context, "Invalid Access Code or Name not set", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUIWithFullName(fullName: String?) {
        fullName?.let {
            // Update user_fullname in LocomotorDisabilityActivity
            activity?.findViewById<TextView>(R.id.user_fullname)?.text = it

            // Update secoff_fullname in SecurityOfficerActivity
            activity?.findViewById<TextView>(R.id.secoff_fullname)?.text = it

            // Update nav_name_header in NavigationView
            val navigationView = activity?.findViewById<NavigationView>(R.id.navigation_view)
            val headerView = navigationView?.getHeaderView(0)
            headerView?.findViewById<TextView>(R.id.nav_name_header)?.text = it
        }
    }

    private fun navigateToActivity(userType: String) {
        when (userType) {
            "Security Officer" -> {
                val intent = Intent(activity, SecurityOfficerActivity::class.java)
                startActivity(intent)
                activity?.finish() // Prevent back navigation
                dismiss()
            }
            "Personnel", "Student", "Visitor" -> {
                val intent = Intent(activity, LocomotorDisabilityActivity::class.java)
                startActivity(intent)
                activity?.finish() // Prevent back navigation
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