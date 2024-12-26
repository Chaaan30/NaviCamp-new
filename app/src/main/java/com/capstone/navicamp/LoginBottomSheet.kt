package com.capstone.navicamp

import com.capstone.navicamp.R
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog

class LoginBottomSheet : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_login, container, false)

        val usernameEditText = view.findViewById<EditText>(R.id.username)
        val loginButton = view.findViewById<Button>(R.id.login_button)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            when (username) {
                "1" -> {
                    val intent = Intent(activity, SecurityOfficerActivity::class.java)
                    startActivity(intent)
                    dismiss()
                }
                "2" -> {
                    val intent = Intent(activity, LocomotorDisabilityActivity::class.java)
                    startActivity(intent)
                    dismiss()
                }
                else -> {
                    // Handle invalid username
                }
            }
        }

        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)

            // Set the gradient background
            val gradientBackground = dialog.findViewById<View>(com.google.android.material.R.id.container)
            gradientBackground?.background = ContextCompat.getDrawable(requireContext(), R.drawable.gradient_background)
        }
        return dialog
    }
}