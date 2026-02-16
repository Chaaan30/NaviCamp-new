package com.capstone.navicamp

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

class VerificationQrGeneratorActivity : AppCompatActivity() {

    private lateinit var roleSpinner: Spinner
    private lateinit var generatedQrImage: ImageView
    private lateinit var generatedQrContent: TextView
    private lateinit var generateButton: MaterialButton
    private var roleSpinnerTouched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_qr_generator)

        roleSpinner = findViewById(R.id.qr_role_spinner)
        generatedQrImage = findViewById(R.id.generated_qr_image)
        generatedQrContent = findViewById(R.id.generated_qr_content)
        generateButton = findViewById(R.id.generate_qr_button)

        val roleOptions = arrayOf("Verification Type", "Disabled User Verification", "Safety Officer Verification")
        val adapter = ArrayAdapter(this, R.layout.spinner_register_selected_item, roleOptions).apply {
            setDropDownViewResource(R.layout.spinner_register_dropdown_item)
        }
        roleSpinner.adapter = adapter

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

            val staffUserID = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .getString("userID", null)
                ?.trim()

            if (staffUserID.isNullOrBlank()) {
                Toast.makeText(this, "Staff user ID not found. Please login again.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val roleToken = if (selectedRole == "Safety Officer Verification") {
                "SAFETY_OFFICER"
            } else {
                "DISABLED"
            }

            val qrContent = "NAVICAMP_VERIFY|ROLE=$roleToken|STAFF=$staffUserID"
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
