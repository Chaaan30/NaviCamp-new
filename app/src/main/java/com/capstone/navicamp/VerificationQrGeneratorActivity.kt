package com.capstone.navicamp

import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class VerificationQrGeneratorActivity : AppCompatActivity() {

    private lateinit var roleSpinner: Spinner
    private lateinit var generatedQrImage: ImageView
    private lateinit var generatedQrContent: TextView
    private lateinit var generateButton: MaterialButton
    private var roleSpinnerTouched = false
    private var staffUserID: String? = null
    private var allowedRoleOptions: List<String> = emptyList()
    private val qrZoneId = ZoneId.of("UTC+8")
    private val expiryUiHandler = Handler(Looper.getMainLooper())
    private var expiryTicker: Runnable? = null
    private var scanStatusLookupInProgress = false
    private var qrScanned = false

    private val opsDepartments = setOf("CDMO", "IFO", "SECURITY SERVICES")
    private val chswDepartment = "CHSW"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_qr_generator)

        roleSpinner = findViewById(R.id.qr_role_spinner)
        generatedQrImage = findViewById(R.id.generated_qr_image)
        generatedQrContent = findViewById(R.id.generated_qr_content)
        generateButton = findViewById(R.id.generate_qr_button)
        staffUserID = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            .getString("userID", null)
            ?.trim()

        if (staffUserID.isNullOrBlank()) {
            Toast.makeText(this, "Unable to verify access. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        roleSpinner.visibility = View.GONE
        generateButton.visibility = View.GONE
        generatedQrImage.visibility = View.GONE
        generatedQrContent.text = "Checking access..."
        generatedQrContent.textSize = 14f

        lifecycleScope.launch {
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
                this@VerificationQrGeneratorActivity,
                R.layout.spinner_register_selected_item,
                roleOptions
            ).apply {
                setDropDownViewResource(R.layout.spinner_register_dropdown_item)
            }
            roleSpinner.adapter = adapter

            generatedQrContent.text = ""
            roleSpinner.visibility = View.VISIBLE
            generateButton.visibility = View.VISIBLE
            generatedQrImage.visibility = View.VISIBLE
        }

        roleSpinner.setOnTouchListener { _, _ ->
            roleSpinnerTouched = true
            false
        }

        roleSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (roleSpinnerTouched && position == 0) {
                    Toast.makeText(
                        this@VerificationQrGeneratorActivity,
                        "Select from the dropdown choices.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) = Unit
        }

        generateButton.setOnClickListener {
            showPasswordAuthorizationDialog {
                generateQr()
            }
        }
    }

    private fun showPasswordAuthorizationDialog(onAuthorized: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_qr_password_auth, null)
        val passwordLayout = dialogView.findViewById<TextInputLayout>(R.id.qr_password_input_layout)
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.qr_password_input)
        val cancelBtn = dialogView.findViewById<Button>(R.id.qr_password_cancel_btn)
        val authorizeBtn = dialogView.findViewById<Button>(R.id.qr_password_authorize_btn)

        val dialog = AlertDialog.Builder(this)
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
            lifecycleScope.launch {
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

    private fun generateQr() {
        val selectedRole = roleSpinner.selectedItem.toString()
        if (selectedRole == "Verification Type") {
            Toast.makeText(this, "Select from the dropdown choices.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!allowedRoleOptions.contains(selectedRole)) {
            Toast.makeText(this, "You are not allowed to generate this verification type.", Toast.LENGTH_SHORT).show()
            return
        }

        val roleToken = when (selectedRole) {
            "Safety Officer Verification" -> "SAFETY_OFFICER"
            "Admin Verification" -> "ADMIN"
            "Temporarily Disabled User Verification", "Permanently Disabled User Verification" -> "DISABLED"
            else -> {
                Toast.makeText(this, "Unsupported verification type.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val qrContentTail = when (selectedRole) {
            "Temporarily Disabled User Verification" -> {
                val until = ZonedDateTime.now(qrZoneId).plusDays(30)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                "|MODE=TEMPORARY|UNTIL=$until"
            }
            "Permanently Disabled User Verification" -> "|MODE=PERMANENT"
            else -> ""
        }

        lifecycleScope.launch {
            val nonce = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US)
            val expiresAt = ZonedDateTime.now(qrZoneId).plusMinutes(10)
            val issued = withContext(Dispatchers.IO) {
                MySQLHelper.issueVerificationQrNonce(nonce, staffUserID!!, roleToken, expiresAt)
            }
            if (!issued) {
                Toast.makeText(this@VerificationQrGeneratorActivity, "Failed to issue one-time QR. Please try again.", Toast.LENGTH_LONG).show()
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
                    lifecycleScope.launch {
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

    override fun onDestroy() {
        stopExpiryLiveTimer()
        super.onDestroy()
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
