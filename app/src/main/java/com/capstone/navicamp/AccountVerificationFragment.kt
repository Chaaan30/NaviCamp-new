package com.capstone.navicamp

import android.content.Context
import android.graphics.Bitmap
import android.app.DatePickerDialog
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter

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
    private var allowedRoleOptions: List<String> = emptyList()
    private val qrZoneId = ZoneId.of("UTC+8")
    private val expiryUiHandler = Handler(Looper.getMainLooper())
    private var expiryTicker: Runnable? = null
    private var scanStatusLookupInProgress = false
    private var qrScanned = false

    private val opsDepartments = setOf("CDMO", "IFO", "SECURITY SERVICES")
    private val chswDepartment = "CHSW"

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
            val access = withContext(Dispatchers.IO) {
                MySQLHelper.getVerificationGeneratorAccess(staffUserID!!)
            }

            if (access == null || !access.isAdmin) {
                generatedQrContent.text = "Only admin safety officers can generate verification QR."
                return@launch
            }

            val departmentKey = access.department.trim().uppercase(Locale.US)
            allowedRoleOptions = when {
                opsDepartments.contains(departmentKey) -> listOf(
                    "Safety Officer Verification",
                    "Admin Verification"
                )
                departmentKey == chswDepartment -> listOf(
                    "Temporarily Disabled User Verification",
                    "Permanently Disabled User Verification"
                )
                else -> emptyList()
            }

            if (allowedRoleOptions.isEmpty()) {
                generatedQrContent.text = "Your department is not allowed to generate verification QR."
                return@launch
            }

            val roleOptions = mutableListOf("Verification Type").apply {
                addAll(allowedRoleOptions)
            }
            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.spinner_register_selected_item,
                roleOptions
            ).apply {
                setDropDownViewResource(R.layout.spinner_register_dropdown_item)
            }
            roleSpinner.adapter = adapter

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
                    Toast.makeText(requireContext(), "Select from the dropdown choices.", Toast.LENGTH_SHORT).show()
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
            showPasswordAuthorizationDialog {
                generateQrLogic()
            }
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
        // New policy uses explicit dropdown role options. Keep legacy controls hidden.
        disabledVerificationOptionsContainer.visibility = View.GONE
        disabledRoleRadioGroup.clearCheck()

        val isTemporary = selectedRole == "Temporarily Disabled User Verification"
        temporaryValidUntilContainer.visibility = if (isTemporary) View.VISIBLE else View.GONE
        if (!isTemporary) {
            selectedValidUntil = null
            validUntilInput.setText("")
        }
    }

    private fun showPasswordAuthorizationDialog(onAuthorized: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_qr_password_auth, null)
        val passwordLayout = dialogView.findViewById<TextInputLayout>(R.id.qr_password_input_layout)
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.qr_password_input)
        val cancelBtn = dialogView.findViewById<Button>(R.id.qr_password_cancel_btn)
        val authorizeBtn = dialogView.findViewById<Button>(R.id.qr_password_authorize_btn)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        cancelBtn.setOnClickListener { dialog.dismiss() }

        authorizeBtn.setOnClickListener {
            val password = passwordInput.text?.toString()?.trim().orEmpty()
            passwordLayout.error = null
            if (password.isBlank()) {
                passwordLayout.error = "Password is required"
                return@setOnClickListener
            }
            authorizeBtn.isEnabled = false
            authorizeBtn.text = "Checking..."
            viewLifecycleOwner.lifecycleScope.launch {
                val valid = withContext(Dispatchers.IO) {
                    MySQLHelper.validateUserPasswordByUserID(staffUserID!!, password)
                }
                authorizeBtn.isEnabled = true
                authorizeBtn.text = "Authorize"
                if (!valid) {
                    passwordLayout.error = "Incorrect password"
                    return@launch
                }
                dialog.dismiss()
                onAuthorized()
            }
        }

        dialog.show()
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
            Toast.makeText(requireContext(), "Select from the dropdown choices.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!allowedRoleOptions.contains(selectedRole)) {
            Toast.makeText(requireContext(), "You are not allowed to generate this verification type.", Toast.LENGTH_SHORT).show()
            return
        }

        val roleToken = when (selectedRole) {
            "Safety Officer Verification" -> "SAFETY_OFFICER"
            "Admin Verification" -> "ADMIN"
            "Temporarily Disabled User Verification", "Permanently Disabled User Verification" -> "DISABLED"
            else -> {
                Toast.makeText(requireContext(), "Unsupported verification type.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val qrContentTail = when (selectedRole) {
            "Temporarily Disabled User Verification" -> {
                val validUntil = selectedValidUntil
                if (validUntil.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Please pick a valid date for temporary access.", Toast.LENGTH_SHORT).show()
                    return
                }
                "|MODE=TEMPORARY|UNTIL=$validUntil"
            }
            "Permanently Disabled User Verification" -> "|MODE=PERMANENT"
            else -> ""
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val nonce = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US)
            val expiresAt = ZonedDateTime.now(qrZoneId).plusMinutes(10)
            val issued = withContext(Dispatchers.IO) {
                MySQLHelper.issueVerificationQrNonce(nonce, staffUserID!!, roleToken, expiresAt)
            }
            if (!issued) {
                Toast.makeText(requireContext(), "Failed to issue one-time QR. Please try again.", Toast.LENGTH_LONG).show()
                return@launch
            }

            val expPayloadText = expiresAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val qrContent = "NAVICAMP_VERIFY|ROLE=$roleToken|STAFF=${staffUserID!!}|NONCE=$nonce|EXP=$expPayloadText$qrContentTail"
            generatedQrImage.setImageBitmap(generateQrBitmap(qrContent, 760))
            qrScanned = false
            scanStatusLookupInProgress = false
            startExpiryLiveTimer(expiresAt, nonce, roleToken)
        }
    }

    private fun startExpiryLiveTimer(expiresAt: ZonedDateTime, nonce: String, roleToken: String) {
        stopExpiryLiveTimer()
        val displayFormatter = DateTimeFormatter.ofPattern("MMMM-dd-yyyy hh:mm:ss a", Locale.US)
        val ownerStaffUserID = staffUserID.orEmpty()
        var tickCount = 0

        val ticker = object : Runnable {
            override fun run() {
                val now = ZonedDateTime.now(qrZoneId)
                val remainingSeconds = Duration.between(now, expiresAt).seconds

                if (remainingSeconds <= 0) {
                    generatedQrContent.text = "One-time QR expired at ${expiresAt.format(displayFormatter)}\nLive Timer: 00:00\nScan Status: ${if (qrScanned) "Scanned" else "Not yet scanned"}"
                    return
                }

                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                generatedQrContent.text = "One-time QR ready. Expires at ${expiresAt.format(displayFormatter)}\nLive Timer: ${String.format(Locale.US, "%02d:%02d", minutes, seconds)}\nScan Status: ${if (qrScanned) "Scanned" else "Not yet scanned"}"

                tickCount++
                if (!qrScanned && !scanStatusLookupInProgress && ownerStaffUserID.isNotBlank() && tickCount % 3 == 0) {
                    scanStatusLookupInProgress = true
                    viewLifecycleOwner.lifecycleScope.launch {
                        val isConsumed = withContext(Dispatchers.IO) {
                            MySQLHelper.isVerificationQrNonceConsumed(
                                nonce = nonce,
                                staffUserID = ownerStaffUserID,
                                roleToken = roleToken
                            )
                        }
                        if (isConsumed) {
                            qrScanned = true
                        }
                        scanStatusLookupInProgress = false
                    }
                }
                expiryUiHandler.postDelayed(this, 1000L)
            }
        }

        expiryTicker = ticker
        ticker.run()
    }

    private fun stopExpiryLiveTimer() {
        expiryTicker?.let { expiryUiHandler.removeCallbacks(it) }
        expiryTicker = null
        scanStatusLookupInProgress = false
    }

    override fun onDestroyView() {
        stopExpiryLiveTimer()
        super.onDestroyView()
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