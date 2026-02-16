package com.capstone.navicamp

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VerificationQrGeneratorActivity : AppCompatActivity() {

    private lateinit var roleSpinner: Spinner
    private lateinit var generatedQrImage: ImageView
    private lateinit var generatedQrContent: TextView
    private lateinit var generateButton: MaterialButton
    private var roleSpinnerTouched = false
    private var staffUserID: String? = null

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

        val roleOptions = arrayOf("Verification Type", "Disabled User Verification", "Safety Officer Verification")
        val adapter = ArrayAdapter(this, R.layout.spinner_register_selected_item, roleOptions).apply {
            setDropDownViewResource(R.layout.spinner_register_dropdown_item)
        }
        roleSpinner.adapter = adapter

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
            val isAdmin = withContext(Dispatchers.IO) {
                MySQLHelper.isSafetyOfficerAdmin(staffUserID!!)
            }
            if (!isAdmin) {
                generatedQrContent.text = "Only admin safety officers can generate verification QR."
                return@launch
            }

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
                        "Please select a verification type.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) = Unit
        }

        generateButton.setOnClickListener {
            val selectedRole = roleSpinner.selectedItem.toString()
            if (selectedRole == "Verification Type") {
                Toast.makeText(this, "Please select a verification type.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val roleToken = if (selectedRole == "Safety Officer Verification") {
                "SAFETY_OFFICER"
            } else {
                "DISABLED"
            }

            val qrContent = "NAVICAMP_VERIFY|ROLE=$roleToken|STAFF=${staffUserID!!}"
            generatedQrImage.setImageBitmap(generateQrBitmap(qrContent, 760))
            generatedQrContent.text = qrContent
        }
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
