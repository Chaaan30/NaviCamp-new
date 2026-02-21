package com.capstone.navicamp

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountVerificationFragment : Fragment(R.layout.fragment_account_verification) {

    private lateinit var roleSpinner: Spinner
    private lateinit var generatedQrImage: ImageView
    private lateinit var generatedQrContent: TextView
    private lateinit var generateButton: MaterialButton

    private var roleSpinnerTouched = false
    private var staffUserID: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize Views
        roleSpinner = view.findViewById(R.id.qr_role_spinner)
        generatedQrImage = view.findViewById(R.id.generated_qr_image)
        generatedQrContent = view.findViewById(R.id.generated_qr_content)
        generateButton = view.findViewById(R.id.generate_qr_button)

        // 2. Access SharedPreferences via requireContext()
        staffUserID = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            .getString("userID", null)
            ?.trim()

        // 3. Setup Spinner
        val roleOptions = arrayOf("Verification Type", "Disabled User Verification", "Safety Officer Verification")
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_register_selected_item, roleOptions).apply {
            setDropDownViewResource(R.layout.spinner_register_dropdown_item)
        }
        roleSpinner.adapter = adapter

        // 4. Security Check
        if (staffUserID.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Unable to verify access. Please login again.", Toast.LENGTH_LONG).show()
            return
        }

        // Initial UI state
        setViewsVisibility(View.GONE)
        generatedQrContent.text = "Checking access..."

        // 5. Admin Validation using viewLifecycleOwner
        viewLifecycleOwner.lifecycleScope.launch {
            val isAdmin = withContext(Dispatchers.IO) {
                MySQLHelper.isSafetyOfficerAdmin(staffUserID!!)
            }

            if (!isAdmin) {
                generatedQrContent.text = "Only admin safety officers can generate verification QR."
                return@launch
            }

            generatedQrContent.text = ""
            setViewsVisibility(View.VISIBLE)
        }

        // 6. Listeners
        roleSpinner.setOnTouchListener { _, _ ->
            roleSpinnerTouched = true
            false
        }

        roleSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                if (roleSpinnerTouched && position == 0) {
                    Toast.makeText(requireContext(), "Please select a verification type.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) = Unit
        }

        generateButton.setOnClickListener {
            generateQrLogic()
        }
    }

    private fun setViewsVisibility(visibility: Int) {
        roleSpinner.visibility = visibility
        generateButton.visibility = visibility
        generatedQrImage.visibility = visibility
    }

    private fun generateQrLogic() {
        val selectedRole = roleSpinner.selectedItem.toString()
        if (selectedRole == "Verification Type") {
            Toast.makeText(requireContext(), "Please select a verification type.", Toast.LENGTH_SHORT).show()
            return
        }

        val roleToken = if (selectedRole == "Safety Officer Verification") {
            "SAFETY_OFFICER"
        } else {
            "DISABLED"
        }

        val qrContent = "NAVICAMP_VERIFY|ROLE=$roleToken|STAFF=${staffUserID!!}"
        generatedQrImage.setImageBitmap(generateQrBitmap(qrContent, 760))
        generatedQrContent.text = "QR Content: $qrContent"
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }


}