package com.capstone.navicamp

import android.content.Context
import android.graphics.Bitmap
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
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
import java.util.Calendar
import java.util.Locale

class AccountVerificationFragment : Fragment(R.layout.fragment_account_verification) {

    private lateinit var roleSpinner: Spinner
    private lateinit var generatedQrImage: ImageView
    private lateinit var generatedQrContent: TextView
    private lateinit var generateButton: MaterialButton
    private lateinit var disabledVerificationOptionsContainer: View
    private lateinit var disabledRoleRadioGroup: RadioGroup
    private lateinit var temporaryValidUntilContainer: View
    private lateinit var validUntilInput: EditText

    private var roleSpinnerTouched = false
    private var staffUserID: String? = null
    private var selectedValidUntil: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize Views
        roleSpinner = view.findViewById(R.id.qr_role_spinner)
        generatedQrImage = view.findViewById(R.id.generated_qr_image)
        generatedQrContent = view.findViewById(R.id.generated_qr_content)
        generateButton = view.findViewById(R.id.generate_qr_button)
        disabledVerificationOptionsContainer = view.findViewById(R.id.disabled_verification_options_container)
        disabledRoleRadioGroup = view.findViewById(R.id.disabled_role_radio_group)
        temporaryValidUntilContainer = view.findViewById(R.id.temporary_valid_until_container)
        validUntilInput = view.findViewById(R.id.verification_valid_until_input)

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
                val selectedRole = roleSpinner.selectedItem.toString()
                updateDisabledVerificationUi(selectedRole)
                if (roleSpinnerTouched && position == 0) {
                    Toast.makeText(requireContext(), "Please select a verification type.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) = Unit
        }

        disabledRoleRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isTemporary = checkedId == R.id.radio_temporary
            temporaryValidUntilContainer.visibility = if (isTemporary) View.VISIBLE else View.GONE

            if (!isTemporary) {
                selectedValidUntil = null
                validUntilInput.setText("")
            }
        }

        validUntilInput.setOnClickListener {
            showDatePicker()
        }

        generateButton.setOnClickListener {
            generateQrLogic()
        }
    }

    private fun setViewsVisibility(visibility: Int) {
        roleSpinner.visibility = visibility
        generateButton.visibility = visibility
        generatedQrImage.visibility = visibility
        if (visibility != View.VISIBLE) {
            disabledVerificationOptionsContainer.visibility = View.GONE
        } else {
            updateDisabledVerificationUi(roleSpinner.selectedItem?.toString().orEmpty())
        }
    }

    private fun updateDisabledVerificationUi(selectedRole: String) {
        val isDisabledVerification = selectedRole == "Disabled User Verification"
        disabledVerificationOptionsContainer.visibility = if (isDisabledVerification) View.VISIBLE else View.GONE

        if (!isDisabledVerification) {
            disabledRoleRadioGroup.clearCheck()
            temporaryValidUntilContainer.visibility = View.GONE
            selectedValidUntil = null
            validUntilInput.setText("")
        }
    }

    private fun showDatePicker() {
        val now = Calendar.getInstance()
        val picker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedValidUntil = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                validUntilInput.setText(selectedValidUntil)
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        )
        picker.datePicker.minDate = now.timeInMillis
        picker.show()
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

        val qrContent = if (selectedRole == "Disabled User Verification") {
            val selectedDisabledRoleId = disabledRoleRadioGroup.checkedRadioButtonId
            if (selectedDisabledRoleId == -1) {
                Toast.makeText(requireContext(), "Please select Temporary or Permanent.", Toast.LENGTH_SHORT).show()
                return
            }

            if (selectedDisabledRoleId == R.id.radio_temporary) {
                val validUntil = selectedValidUntil
                if (validUntil.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Please pick a valid date for temporary access.", Toast.LENGTH_SHORT).show()
                    return
                }
                "NAVICAMP_VERIFY|ROLE=$roleToken|STAFF=${staffUserID!!}|MODE=TEMPORARY|UNTIL=$validUntil"
            } else {
                "NAVICAMP_VERIFY|ROLE=$roleToken|STAFF=${staffUserID!!}|MODE=PERMANENT"
            }
        } else {
            "NAVICAMP_VERIFY|ROLE=$roleToken|STAFF=${staffUserID!!}"
        }
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