package com.capstone.navicamp

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.PutObjectRequest
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.Random
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.widget.TooltipCompat
import android.widget.PopupWindow
import android.widget.TextView
import android.Manifest
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.io.InputStream
import android.content.Context

class RegisterBottomSheet : BottomSheetDialogFragment() {

    private lateinit var userTypeSpinner: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var fullNameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var contactNumberEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var otpEditText: EditText
    private lateinit var sendOtpButton: Button
    private lateinit var sendOtpProgressBar: ProgressBar
    private lateinit var otpTimerTextView: TextView
    private lateinit var termsConditionsCheckbox: CheckBox
    private lateinit var termsConditionsText: TextView
    private lateinit var termsConditionsLabel: TextView
    private lateinit var uploadProofButton: Button
    private lateinit var imageContainer: LinearLayout
    private lateinit var selectedImageName: TextView
    private lateinit var removeImageButton: Button
    private var selectedImageUri: Uri? = null
    private var uploadedImageName: String? = null
    private val PICK_IMAGE_REQUEST = 1
    private var generatedOtp: String? = null
    private var isOtpSent: Boolean = false
    private val REQUEST_IMAGE_CAPTURE = 2
    private lateinit var currentPhotoPath: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_register, container, false)

        userTypeSpinner = view.findViewById(R.id.user_type_spinner)
        fullNameEditText = view.findViewById(R.id.fullname)
        emailEditText = view.findViewById(R.id.email)
        contactNumberEditText = view.findViewById(R.id.contact_number)
        passwordEditText = view.findViewById(R.id.password)
        confirmPasswordEditText = view.findViewById(R.id.confirm_password)
        otpEditText = view.findViewById(R.id.otp)
        sendOtpButton = view.findViewById(R.id.send_otp)
        sendOtpProgressBar = view.findViewById(R.id.send_otp_progress_bar)
        otpTimerTextView = view.findViewById(R.id.otp_timer)
        termsConditionsCheckbox = view.findViewById(R.id.terms_conditions_checkbox)
        termsConditionsText = view.findViewById(R.id.terms_conditions_text)
        termsConditionsLabel = view.findViewById(R.id.terms_conditions_label)
        uploadProofButton = view.findViewById(R.id.upload_proof_button)
        imageContainer = view.findViewById(R.id.image_container)
        selectedImageName = view.findViewById(R.id.selected_image_name)
        removeImageButton = view.findViewById(R.id.remove_image_button)

        uploadProofButton.setOnClickListener { view ->
            onUploadProofButtonClick(view)
        }

        removeImageButton.setOnClickListener {
            selectedImageUri = null
            uploadedImageName = null
            imageContainer.visibility = View.GONE
            uploadProofButton.visibility = View.VISIBLE
        }

        val infoButton = view.findViewById<ImageButton>(R.id.info_button)
        infoButton.setOnClickListener {
            showInfoDialog()
        }

        // Set input filter for contact number to accept only 11 digits
        contactNumberEditText.filters = arrayOf(InputFilter.LengthFilter(11), InputFilter { source, _, _, dest, _, _ ->
            if (source.isEmpty()) return@InputFilter null // Allow deletion
            if (source.length + dest.length > 11) return@InputFilter "" // Restrict to 11 digits
            if (!source.matches(Regex("\\d+"))) return@InputFilter "" // Allow only digits
            null
        })

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.user_types_reg,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            userTypeSpinner.adapter = adapter
        }

        sendOtpButton.setOnClickListener {
            val email = emailEditText.text.toString()
            if (email.isNotEmpty()) {
                sendOtpButton.visibility = View.GONE
                sendOtpProgressBar.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    generatedOtp = generateOtp()
                    sendOtpEmail(email, generatedOtp!!)
                    sendOtpProgressBar.visibility = View.GONE
                    otpEditText.visibility = View.VISIBLE
                    startOtpTimer()
                    isOtpSent = true
                }
            } else {
                Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
            }
        }

        val loginButton = view.findViewById<Button>(R.id.login)
        loginButton.setOnClickListener {
            val loginBottomSheet = LoginBottomSheet()
            loginBottomSheet.show(parentFragmentManager, "LoginBottomSheet")
            dismiss()
        }

        val createAccountButton = view.findViewById<Button>(R.id.create_account)
        createAccountButton.setOnClickListener {
            registerUser(view)
        }

        termsConditionsText.setOnClickListener {
            val dialogView = inflater.inflate(R.layout.dialog_terms_conditions, null)
            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .show()
        }

        return view
    }

    private fun startOtpTimer() {
        sendOtpButton.visibility = View.GONE
        otpTimerTextView.visibility = View.VISIBLE
        object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                otpTimerTextView.text = "Resend in\n${millisUntilFinished / 1000}"
            }

            override fun onFinish() {
                otpTimerTextView.visibility = View.GONE
                sendOtpButton.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun onUploadProofButtonClick(view: View) {
        openImagePicker()
    }

    private fun registerUser(view: View) {
        val fullName = fullNameEditText.text.toString()
        val email = emailEditText.text.toString()
        val contactNumber = contactNumberEditText.text.toString()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val otp = otpEditText.text.toString()
        val userType = userTypeSpinner.selectedItem.toString()

        // Validation
        if (fullName.isEmpty() || email.isEmpty() || contactNumber.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || otp.isEmpty()) {
            Toast.makeText(context, "Please fill all the fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (userType == "User Types") {
            Toast.makeText(context, "Please select a valid user type", Toast.LENGTH_SHORT).show()
            return
        }

        if (!termsConditionsCheckbox.isChecked) {
            Toast.makeText(context, "Please read and agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isOtpSent) {
            Toast.makeText(context, "Please send the OTP", Toast.LENGTH_SHORT).show()
            return
        }

        if (otp != generatedOtp) {
            Toast.makeText(context, "Invalid OTP", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 7) {
            Toast.makeText(context, "Password must be at least 7 characters long", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        if (contactNumber.length != 11) {
            Toast.makeText(context, "Contact number must be exactly 11 digits", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedImageUri == null) {
            Toast.makeText(context, "Please select an image for proof of disability", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading dialog
        val loadingDialog = showLoadingDialog()

        // Hash the password
        val hashedPassword = hashPassword(password)

        // Upload image and register user
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (selectedImageUri != null) {
                    uploadedImageName = withContext(Dispatchers.IO) {
                        uploadImageToS3(context!!, selectedImageUri!!)
                    }
                }
                val userID: String? = withContext(Dispatchers.IO) {
                    MySQLHelper.generateUserID()
                }
                val isInserted = withContext(Dispatchers.IO) {
                    MySQLHelper.insertUser(
                        userID ?: "",
                        fullName,
                        userType,
                        email,
                        contactNumber,
                        hashedPassword,
                        uploadedImageName
                    )
                }
                loadingDialog.dismiss()
                if (isInserted) {
                    Toast.makeText(context, "User registered successfully", Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    Toast.makeText(context, "Registration failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Toast.makeText(context, "Image upload failed. Try Again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashedBytes = md.digest(password.toByteArray())
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateOtp(): String {
        val random = Random()
        val otp = StringBuilder()
        for (i in 0 until 6) {
            otp.append(random.nextInt(10))
        }
        return otp.toString()
    }

    private suspend fun sendOtpEmail(toEmail: String, otp: String) {
        withContext(Dispatchers.IO) {
            val username = "navicamp.noreply@gmail.com"
            val password = "tcwp hour hlzz tcag" // App password

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }

            val session = Session.getInstance(props,
                object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(username, password)
                    }
                })

            try {
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(username))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    subject = "Your OTP Code"
                    setText("Your OTP code is: $otp")
                }
                Transport.send(message)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun loadAwsCredentials(): BasicAWSCredentials {
        val props = Properties()
        val assetManager = context?.assets
        val inputStream: InputStream = assetManager?.open("config.properties")!!
        props.load(inputStream)
        val accessKey = props.getProperty("aws.accessKeyId")
        val secretKey = props.getProperty("aws.secretKey")
        return BasicAWSCredentials(accessKey, secretKey)
    }

    private suspend fun uploadImageToS3(context: Context, imageUri: Uri): String {
        AwsUtils.initialize(context)
        val s3Client = AwsUtils.s3Client
        val bucketName = "navicampbucket"

        val filePath = getPathFromUri(imageUri)
        val file = File(filePath)
        val fileName = "proof_of_disability/${file.name}"

        val putObjectRequest = PutObjectRequest(bucketName, fileName, file)

        return withContext(Dispatchers.IO) {
            s3Client.putObject(putObjectRequest)
            fileName
        }
    }

    private fun getPathFromUri(uri: Uri): String {
        var path: String? = null
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = requireContext().contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                path = it.getString(columnIndex)
            }
        }
        return path ?: ""
    }

    fun showLoadingDialog(): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_loading)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        return dialog
    }

    private fun showInfoDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_info, null)
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(requireContext().packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        requireContext(),
                        "com.capstone.navicamp.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data
            selectedImageUri?.let {
                selectedImageName.text = File(getPathFromUri(it)).name
                imageContainer.visibility = View.VISIBLE
                uploadProofButton.visibility = View.GONE
            }
        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            selectedImageUri = Uri.fromFile(File(currentPhotoPath))
            selectedImageUri?.let {
                selectedImageName.text = File(currentPhotoPath).name
                imageContainer.visibility = View.VISIBLE
                uploadProofButton.visibility = View.GONE
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)
        }
        return dialog
    }
}