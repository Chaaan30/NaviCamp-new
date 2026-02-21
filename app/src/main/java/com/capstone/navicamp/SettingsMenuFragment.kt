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
            // Logic A: PWD Flow (Student/Employee + Temporary/Permanent)
            val isPwdUser = (userType == "Student" || userType == "Employee") &&
                    (systemRole == "Temporary" || systemRole == "Permanent")

            // Logic B: Officer Flow (Employee + Safety Officer/Admin)
            val isOfficerUser = (userType == "Employee") &&
                    (systemRole == "Safety Officer" || systemRole == "Admin")

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

        // 4. Logout
        view.findViewById<TextView>(R.id.btn_logout).setOnClickListener {
            prefs.edit().clear().apply()
            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
        }
    }
}