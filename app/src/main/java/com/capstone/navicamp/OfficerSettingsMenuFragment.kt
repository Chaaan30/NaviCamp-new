package com.capstone.navicamp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.capstone.navicamp.R

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [OfficerSettingsMenuFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class OfficerSettingsMenuFragment : Fragment(R.layout.fragment_officer_settings_menu) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Display Name
        val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        view.findViewById<TextView>(R.id.txt_full_name).text = prefs.getString("fullName", "Officer")

        // 2. Go to Account Details (This is where the swap happens)
        view.findViewById<View>(R.id.profile_section).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.officer_fragment_container, OfficerAccountSettingsFragment())
                .addToBackStack(null) // This allows the "Back" button to work
                .commit()
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