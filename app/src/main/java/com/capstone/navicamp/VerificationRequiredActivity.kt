package com.capstone.navicamp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class VerificationRequiredActivity : AppCompatActivity() {

    private lateinit var userID: String
    private lateinit var systemRole: String
    private lateinit var fullName: String
    private lateinit var campusAffiliation: String
    private lateinit var email: String
    private lateinit var contactNumber: String
    private lateinit var createdOn: String
    private lateinit var updatedOn: String

    private val qrCodeScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        val scannedContent = result.contents?.trim()
        if (scannedContent.isNullOrBlank()) {
            return@registerForActivityResult
        }

        val payload = parseVerificationQrPayload(scannedContent)
        val allowedRoleTokens = acceptedRoleTokensForRole(systemRole)
        if (payload == null || !allowedRoleTokens.contains(payload.roleToken)) {
            Toast.makeText(this, "Invalid verification QR", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        if (payload.roleToken == "DISABLED" && !isValidDisabledVerificationPayload(payload)) {
            Toast.makeText(this, "Invalid verification QR", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        CoroutineScope(Dispatchers.Main).launch {
            val staffExists = withContext(Dispatchers.IO) {
                MySQLHelper.doesUserIDExist(payload.staffUserID)
            }
            if (!staffExists) {
                Toast.makeText(
                    this@VerificationRequiredActivity,
                    "Invalid verification QR: staff account not found.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val tokenAccepted = withContext(Dispatchers.IO) {
                MySQLHelper.consumeVerificationQrNonce(
                    nonce = payload.nonce,
                    staffUserID = payload.staffUserID,
                    roleToken = payload.roleToken
                )
            }
            if (!tokenAccepted) {
                Toast.makeText(
                    this@VerificationRequiredActivity,
                    "This QR is already used or expired.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val isUpdated = withContext(Dispatchers.IO) {
                MySQLHelper.approveUserVerificationWithProfileAudit(
                    userID = userID,
                    staffUserID = payload.staffUserID,
                    disabledVerificationMode = payload.disabledVerificationMode,
                    temporaryValidUntil = payload.validUntil,
                    promoteToAdmin = payload.roleToken == "ADMIN" && isSafetyOfficerRole(systemRole)
                )
            }

            if (!isUpdated) {
                Toast.makeText(
                    this@VerificationRequiredActivity,
                    "Failed to update verification status. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val updatedSystemRole = if (payload.roleToken == "ADMIN" && isSafetyOfficerRole(systemRole)) {
                "Admin"
            } else {
                systemRole
            }

            with(userPrefs.edit()) {
                putString("userID", userID)
                putString("fullName", fullName)
                putString("userType", campusAffiliation)
                putString("email", email)
                putString("contactNumber", contactNumber)
                putString("createdOn", createdOn)
                putString("updatedOn", updatedOn)
                putString("systemRole", updatedSystemRole)
                putBoolean("isLoggedIn", true)
                apply()
            }
            UserSingleton.fullName = fullName

            Toast.makeText(
                this@VerificationRequiredActivity,
                "Verification approved. Redirecting...",
                Toast.LENGTH_SHORT
            ).show()

            val destination = if (isSafetyOfficerRole(systemRole)) {
                Intent(this@VerificationRequiredActivity, SecurityOfficerActivity::class.java)
            } else {
                Intent(this@VerificationRequiredActivity, LocomotorDisabilityActivity::class.java)
            }
            destination.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(destination)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_required)

        userID = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        systemRole = intent.getStringExtra(EXTRA_SYSTEM_ROLE).orEmpty()
        fullName = intent.getStringExtra(EXTRA_FULL_NAME).orEmpty()
        campusAffiliation = intent.getStringExtra(EXTRA_CAMPUS_AFFILIATION).orEmpty()
        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        contactNumber = intent.getStringExtra(EXTRA_CONTACT_NUMBER).orEmpty()
        createdOn = intent.getStringExtra(EXTRA_CREATED_ON).orEmpty()
        updatedOn = intent.getStringExtra(EXTRA_UPDATED_ON).orEmpty()

        if (userID.isBlank() || systemRole.isBlank()) {
            Toast.makeText(this, "Missing verification details.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val titleText = findViewById<TextView>(R.id.verification_title)
        val descriptionText = findViewById<TextView>(R.id.verification_description)
        val scanButton = findViewById<MaterialButton>(R.id.scan_verification_qr_button)

        if (isSafetyOfficerRole(systemRole)) {
            titleText.text = "Verification Required"
            descriptionText.text =
                "Please visit the admin safety officer for account verification. Once verified, your features will be activated."
            scanButton.text = "Scan Safety Officer/Admin Verification QR"
        } else {
            titleText.text = "Verification Required"
            descriptionText.text =
                "Please visit the CHSW clinic with your Medical Certificate. Once verified, your features will be activated."
            scanButton.text = "Scan Clinic Verification QR"
        }

        scanButton.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan Verification QR")
                setBeepEnabled(true)
                setBarcodeImageEnabled(true)
                setOrientationLocked(true)
            }
            qrCodeScannerLauncher.launch(options)
        }
    }

    private fun isSafetyOfficerRole(role: String): Boolean {
        val value = role.trim().lowercase()
        return value.contains("safety") || value.contains("security") || value.contains("officer")
    }

    private fun acceptedRoleTokensForRole(role: String): Set<String> {
        val normalized = role.trim().lowercase(Locale.US)
        return when {
            normalized.contains("admin") -> setOf("ADMIN")
            isSafetyOfficerRole(role) -> setOf("SAFETY_OFFICER", "ADMIN")
            else -> setOf("DISABLED")
        }
    }

    private data class VerificationQrPayload(
        val roleToken: String,
        val staffUserID: String,
        val nonce: String,
        val expiresAt: String,
        val disabledVerificationMode: String?,
        val validUntil: String?
    )

    private fun parseVerificationQrPayload(content: String): VerificationQrPayload? {
        val sections = content.split("|")
        if (sections.isEmpty() || sections.first() != "NAVICAMP_VERIFY") {
            return null
        }

        val values = mutableMapOf<String, String>()
        for (index in 1 until sections.size) {
            val token = sections[index]
            val keyValue = token.split("=", limit = 2)
            if (keyValue.size != 2) {
                continue
            }
            values[keyValue[0].uppercase(Locale.US)] = keyValue[1].trim()
        }

        val roleToken = values["ROLE"]?.uppercase(Locale.US) ?: return null
        val staffUserID = values["STAFF"] ?: return null
        val nonce = values["NONCE"]?.uppercase(Locale.US) ?: return null
        val expiresAt = values["EXP"]?.trim() ?: return null
        if (!staffUserID.matches(Regex("^\\d+$"))) {
            return null
        }
        if (nonce.length < 16) {
            return null
        }
        if (!isNotExpired(expiresAt)) {
            return null
        }

        val disabledVerificationMode = values["MODE"]?.uppercase(Locale.US)
        val validUntil = values["UNTIL"]?.trim()

        return VerificationQrPayload(
            roleToken = roleToken,
            staffUserID = staffUserID,
            nonce = nonce,
            expiresAt = expiresAt,
            disabledVerificationMode = disabledVerificationMode,
            validUntil = validUntil
        )
    }

    private fun isNotExpired(expiresAt: String): Boolean {
        return try {
            val expiry = OffsetDateTime.parse(expiresAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toZonedDateTime()
            val now = ZonedDateTime.now(ZoneId.of("UTC+8"))
            expiry.isAfter(now)
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidDisabledVerificationPayload(payload: VerificationQrPayload): Boolean {
        return when (payload.disabledVerificationMode) {
            null, "PERMANENT" -> true
            "TEMPORARY" -> !payload.validUntil.isNullOrBlank() && isIsoDate(payload.validUntil)
            else -> false
        }
    }

    private fun isIsoDate(value: String?): Boolean {
        if (value.isNullOrBlank()) {
            return false
        }
        return try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_SYSTEM_ROLE = "extra_system_role"
        const val EXTRA_FULL_NAME = "extra_full_name"
        const val EXTRA_CAMPUS_AFFILIATION = "extra_campus_affiliation"
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_CONTACT_NUMBER = "extra_contact_number"
        const val EXTRA_CREATED_ON = "extra_created_on"
        const val EXTRA_UPDATED_ON = "extra_updated_on"

    }
}
