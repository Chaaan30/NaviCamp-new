package com.capstone.navicamp

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.messaging.FirebaseMessaging

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
        forgotPasswordTextView = view.findViewById(R.id.forgot_password)

        val loginButton = view.findViewById<Button>(R.id.login_button)
        val createAccountButton = view.findViewById<Button>(R.id.create_account_button)

        forgotPasswordTextView.setOnClickListener {
            val forgotPasswordBottomSheet = ForgotPasswordBottomSheet()
            forgotPasswordBottomSheet.show(parentFragmentManager, forgotPasswordBottomSheet.tag)
            dismiss()
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

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = showLoadingDialog()

        CoroutineScope(Dispatchers.Main).launch {
            val startTime = System.currentTimeMillis()
            try {
                val userData = withContext(Dispatchers.IO) {
                    MySQLHelper.loginUser(email, password)
                }

                if (userData != null) {
                    val systemRole = withContext(Dispatchers.IO) {
                        MySQLHelper.getSystemRoleByUserID(userData.userID)
                    }

                    val temporaryAccessExpired = withContext(Dispatchers.IO) {
                        MySQLHelper.isTemporaryUserAccessExpired(userData.userID)
                    }
                    if (temporaryAccessExpired) {
                        showExpiredTemporaryAccountDialog(userData, systemRole)
                        return@launch
                    }

                    when (userData.verified) {
                        1 -> {
                            UserSingleton.fullName = userData.fullName
                            val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                            with(sharedPreferences.edit()) {
                                putString("userID", userData.userID)
                                putString("fullName", userData.fullName)
                                putString("userType", userData.userType)
                                putString("email", userData.email)
                                putString("contactNumber", userData.contactNumber)
                                putString("createdOn", userData.createdOn)
                                putString("updatedOn", userData.updatedOn)
                                putString("systemRole", systemRole)
                                putBoolean("isLoggedIn", true)
                                apply()
                            }

                            // Register FCM token for all users
                                registerFCMToken(userData.userID)

                            val intent = when {
                                isSafetyOfficerRole(systemRole) -> Intent(context, SecurityOfficerActivity::class.java)
                                isDisabledRole(systemRole) -> Intent(context, LocomotorDisabilityActivity::class.java)
                                userData.userType == "Safety Officer" || userData.userType == "Security Officer" ->
                                    Intent(context, SecurityOfficerActivity::class.java)
                                userData.userType == "Temporarily Disabled" || userData.userType == "Permanently Disabled" ->
                                    Intent(context, LocomotorDisabilityActivity::class.java)
                                else -> null
                            }
                            intent?.let {
                                // Clear auth entry screens from back stack so Back exits app.
                                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(it)
                                dismiss()
                            } ?: run {
                                Toast.makeText(
                                    context,
                                    "Unable to determine your role. Please contact support.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        0 -> {
                            if (systemRole == null) {
                                Toast.makeText(
                                    context,
                                    "No system role found for this account. Please contact support.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                val verificationIntent = Intent(context, VerificationRequiredActivity::class.java).apply {
                                    putExtra(VerificationRequiredActivity.EXTRA_USER_ID, userData.userID)
                                    putExtra(VerificationRequiredActivity.EXTRA_SYSTEM_ROLE, systemRole)
                                    putExtra(VerificationRequiredActivity.EXTRA_FULL_NAME, userData.fullName)
                                    putExtra(VerificationRequiredActivity.EXTRA_CAMPUS_AFFILIATION, userData.userType)
                                    putExtra(VerificationRequiredActivity.EXTRA_EMAIL, userData.email)
                                    putExtra(VerificationRequiredActivity.EXTRA_CONTACT_NUMBER, userData.contactNumber)
                                    putExtra(VerificationRequiredActivity.EXTRA_CREATED_ON, userData.createdOn)
                                    putExtra(VerificationRequiredActivity.EXTRA_UPDATED_ON, userData.updatedOn)
                                }
                                startActivity(verificationIntent)
                                dismiss()
                            }
                        }
                        2 -> {
                            Toast.makeText(
                                context,
                                "Your account is currently declined. Please contact support for assistance.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Invalid email or password", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("LoginBottomSheet", "Error during login", e)
                Toast.makeText(context, "An error occurred during login", Toast.LENGTH_SHORT).show()
            } finally {
                loadingDialog.dismiss()
                val endTime = System.currentTimeMillis()
                if (endTime - startTime > 5000) {
                    Toast.makeText(context, "Internet is slow, please try again later", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isSafetyOfficerRole(role: String?): Boolean {
        if (role.isNullOrBlank()) return false
        val normalized = role.trim().lowercase()
        return normalized.contains("safety") || normalized.contains("security") || normalized.contains("officer")
    }

    private fun isDisabledRole(role: String?): Boolean {
        if (role.isNullOrBlank()) return false
        val normalized = role.trim().lowercase()
        return normalized.contains("disabled") ||
            normalized.contains("tempor") ||
            normalized.contains("perman") ||
            normalized.contains("pwd")
    }

    private fun showExpiredTemporaryAccountDialog(userData: UserData, systemRole: String?) {
        if (!isAdded) return

        AlertDialog.Builder(requireContext())
            .setTitle("Temporary Access Expired")
            .setMessage(
                "Your temporary account access has expired.\n\n" +
                    "Go to CHSW/Clinic and ask an admin to reactivate your account.\n\n" +
                    "Do you want to scan a reactivation QR now?"
            )
            .setPositiveButton("Yes") { _, _ ->
                val resolvedRole = systemRole ?: userData.userType
                val verificationIntent = Intent(context, VerificationRequiredActivity::class.java).apply {
                    putExtra(VerificationRequiredActivity.EXTRA_USER_ID, userData.userID)
                    putExtra(VerificationRequiredActivity.EXTRA_SYSTEM_ROLE, resolvedRole)
                    putExtra(VerificationRequiredActivity.EXTRA_FULL_NAME, userData.fullName)
                    putExtra(VerificationRequiredActivity.EXTRA_CAMPUS_AFFILIATION, userData.userType)
                    putExtra(VerificationRequiredActivity.EXTRA_EMAIL, userData.email)
                    putExtra(VerificationRequiredActivity.EXTRA_CONTACT_NUMBER, userData.contactNumber)
                    putExtra(VerificationRequiredActivity.EXTRA_CREATED_ON, userData.createdOn)
                    putExtra(VerificationRequiredActivity.EXTRA_UPDATED_ON, userData.updatedOn)
                }
                startActivity(verificationIntent)
                dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun registerFCMToken(userID: String) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("LoginBottomSheet", "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result
                Log.d("LoginBottomSheet", "FCM Token: $token")

                // Update token in database
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val success = MySQLHelper.updateUserFCMToken(userID, token)
                        if (success) {
                            Log.d("LoginBottomSheet", "FCM token updated successfully for user: $userID")
                        } else {
                            Log.e("LoginBottomSheet", "Failed to update FCM token for user: $userID")
                        }
                    } catch (e: Exception) {
                        Log.e("LoginBottomSheet", "Error updating FCM token: ${e.message}", e)
                    }
                }
            }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            bottomSheet?.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)
        }
        return dialog
    }
}
