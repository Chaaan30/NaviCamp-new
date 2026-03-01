package com.capstone.navicamp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.content.Context
import android.content.Intent
import android.widget.TextView
import android.widget.Toast

class SettingsMenuFragment : Fragment(R.layout.fragment_settings_menu) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val profileSection = view.findViewById<androidx.constraintlayout.widget.ConstraintLayout    >(R.id.profile_section)

        // 1. Display Name
        val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val fullName = prefs.getString("fullName", "User")
        val userType = prefs.getString("userType", "")
        val systemRole = prefs.getString("systemRole", "")

        view.findViewById<TextView>(R.id.txt_full_name).text = fullName

        profileSection.setOnClickListener {
            val normalizedUserType = userType?.trim()?.lowercase().orEmpty()
            val normalizedSystemRole = systemRole?.trim()?.lowercase().orEmpty()

            // Logic A: PWD Flow (Student/Employee + Temporary/Permanent)
            val isPwdUser = (normalizedUserType == "student" || normalizedUserType == "employee") &&
                    (normalizedSystemRole == "temporary" || normalizedSystemRole == "permanent")

            // Logic B: Officer/Admin flow - supports DB role variants.
            val isOfficerUser =
                normalizedSystemRole.contains("admin") ||
                        normalizedSystemRole.contains("officer") ||
                        normalizedUserType.contains("safety officer") ||
                        normalizedUserType.contains("security officer") ||
                        normalizedUserType.contains("admin")

            when {
                isPwdUser -> {
                    // Redirect to Activity for PWDs
                    parentFragmentManager.beginTransaction().replace(R.id.pwd_fragment_container, LocomotorAccountSettingsFragment()).addToBackStack(null).commit()
                }
                isOfficerUser -> {
                    // Redirect to Fragment for Officers
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.officer_fragment_container, OfficerAccountSettingsFragment())
                        .addToBackStack(null)
                        .commit()
                }
                else -> {
                    // Handle cases where user data might be incomplete
                    Toast.makeText(requireContext(), "Profile details unavailable for this role.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 3. Terms & Privacy
        view.findViewById<TextView>(R.id.btn_privacy).setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_terms_conditions, null)
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .show()
        }

        // 4. Device Setup
        view.findViewById<TextView>(R.id.btn_device_setup).setOnClickListener {
            val intent = Intent(requireContext(), SetupActivity::class.java).apply {
                putExtra("RETURN_TO_MAIN", true)
            }
            startActivity(intent)
        }

        // 5. Logout
        view.findViewById<TextView>(R.id.btn_logout).setOnClickListener {
            prefs.edit().clear().apply()
            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
        }
    }
}