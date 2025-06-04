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
import android.util.Log
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
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart
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
    // private lateinit var uploadProofButton: Button // This seems to be a generic one, specific ones are used below
    private lateinit var imageContainer: LinearLayout
    private lateinit var selectedImageName: TextView
    private lateinit var removeImageButton: ImageButton
    private lateinit var uploadGeneralProofButton: Button
    private lateinit var uploadProofOfficerButton: Button
    private lateinit var generalProofLayout: LinearLayout
    private lateinit var officerProofLayout: LinearLayout
    private var selectedImageUri: Uri? = null
    private var uploadedImageName: String? = null
    private lateinit var infoButtonGeneralProof: ImageButton
    private lateinit var infoButtonUploadOfficer: ImageButton
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
        // uploadProofButton = view.findViewById(R.id.upload_proof_button) // Commented out as specific buttons are used
        imageContainer = view.findViewById(R.id.image_container)
        selectedImageName = view.findViewById(R.id.selected_image_name)
        removeImageButton = view.findViewById(R.id.remove_image_button)
        uploadGeneralProofButton = view.findViewById(R.id.upload_general_proof_button)
        uploadProofOfficerButton = view.findViewById(R.id.upload_proof_officer_button)
        generalProofLayout = view.findViewById(R.id.generalProofLayout)
        officerProofLayout = view.findViewById(R.id.officerProofLayout)
        infoButtonGeneralProof = view.findViewById(R.id.info_button_general_proof)
        infoButtonUploadOfficer = view.findViewById(R.id.info_button_upload_officer)

        uploadGeneralProofButton.setOnClickListener {
            onUploadProofButtonClick()
        }
        uploadProofOfficerButton.setOnClickListener {
            onUploadProofButtonClick()
        }

        removeImageButton.setOnClickListener {
            selectedImageUri = null
            uploadedImageName = null
            imageContainer.visibility = View.GONE
            updateProofSectionVisibility()
        }

        infoButtonGeneralProof.setOnClickListener {
            showInfoDialog("general")
        }

        infoButtonUploadOfficer.setOnClickListener {
            showInfoDialog("officer")
        }

        // Commenting out the infoButton for R.id.info_button_upload as it does not exist.
        // Specific info buttons (info_button_upload_disability, info_button_upload_officer) are handled elsewhere if needed.
        // val infoButton = view.findViewById<ImageButton>(R.id.info_button_upload)
        // infoButton.setOnClickListener {
        //     val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_info, null)
        //     val dialog = AlertDialog.Builder(requireContext())
        //         .setView(dialogView)
        //         .setPositiveButton("OK", null)
        //         .create()
        //     dialog.show()
        // }


        // Set input filter for contact number to accept only 11 digits
        contactNumberEditText.filters =
            arrayOf(InputFilter.LengthFilter(11), InputFilter { source, _, _, dest, _, _ ->
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

        userTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateProofSectionVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
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

    private fun onUploadProofButtonClick() {
        val options = arrayOf("Choose from Gallery", "Take Photo")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Proof Image")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> openImagePicker() // Choose from Gallery
                    1 -> dispatchTakePictureIntent() // Take Photo
                }
            }
            .show()
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
            Toast.makeText(
                context,
                "Please read and agree to the Terms and Conditions",
                Toast.LENGTH_SHORT
            ).show()
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
            Toast.makeText(
                context,
                "Password must be at least 7 characters long",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        if (contactNumber.length != 11) {
            Toast.makeText(context, "Contact number must be exactly 11 digits", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (selectedImageUri == null) {
            val userType = userTypeSpinner.selectedItem.toString()
            val proofMessage = when (userType) {
                "Security Officer" -> "Please upload your officer proof."
                "Student", "Personnel", "Visitor" -> "Please upload the required proof."
                else -> "Please upload the required proof." // Default or for other types if any
            }
            Toast.makeText(context, proofMessage, Toast.LENGTH_SHORT).show()
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
                        uploadImageToS3(context!!, selectedImageUri!!, userType)
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
                    if (userType == "Security Officer" && selectedImageUri != null) {
                        // Send verification email to admin for security officer
                        sendOfficerVerificationEmail(email, fullName, userID ?: "", selectedImageUri!!)
                    }
                    dismiss()
                } else {
                    Toast.makeText(context, "Registration failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Log.e("RegisterUser", "Registration or S3/Email failed", e)
                Toast.makeText(context, "Registration process failed. Error: ${e.localizedMessage}", Toast.LENGTH_LONG)
                    .show()
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
                // Optionally, inform the user that the verification email might have failed
                CoroutineScope(Dispatchers.Main).launch {
                     Toast.makeText(context, "User registered, but officer verification email failed to send.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun sendOfficerVerificationEmail(
        officerEmail: String,
        officerFullName: String,
        officerUserID: String,
        imageUri: Uri // Added image URI for attachment
    ) {
        withContext(Dispatchers.IO) {
            val adminEmail = "cristian013003@gmail.com"
            val emailUsername = "navicamp.noreply@gmail.com"
            val emailPassword = "tcwp hour hlzz tcag"

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }

            val session = Session.getInstance(props,
                object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(emailUsername, emailPassword)
                    }
                })

            try {
                val message = MimeMessage(session)
                message.setFrom(InternetAddress(emailUsername))
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(adminEmail))
                message.subject = "New Security Officer Registration - Verification Required"                // Create the email body part
                val textBodyPart = MimeBodyPart()
                // API Gateway endpoint URL
                val baseUrl = "https://q345fygnt4.execute-api.ap-southeast-1.amazonaws.com/verify-action"

                val verifyLink = "$baseUrl?userID=$officerUserID&action=verify"
                val rejectLink = "$baseUrl?userID=$officerUserID&action=reject"

                val emailBody = """
                    <html>
                    <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                        <h2 style="color: #2c5aa0;">New Security Officer Registration - Verification Required</h2>
                        
                        <p>A new Security Officer has registered and requires verification:</p>
                        
                        <div style="background-color: #f8f9fa; padding: 15px; border-radius: 5px; margin: 20px 0;">
                            <p><strong>Name:</strong> $officerFullName</p>
                            <p><strong>Email:</strong> $officerEmail</p>
                            <p><strong>User ID:</strong> $officerUserID</p>
                        </div>
                        
                        <p><strong>The proof of identity is attached to this email.</strong></p>
                        
                        <p>Please review the attached proof and use the buttons below to verify or reject the user:</p>
                        
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="$verifyLink" style="display: inline-block; background-color: #28a745; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; margin: 10px; font-weight: bold;">✓ VERIFY USER</a>
                            <a href="$rejectLink" style="display: inline-block; background-color: #dc3545; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; margin: 10px; font-weight: bold;">✗ REJECT USER</a>
                        </div>
                        
                        <p style="font-size: 12px; color: #666; margin-top: 30px;">
                            <em>Note: If you choose to reject, the user's record will be deleted.</em>
                        </p>
                    </body>
                    </html>
                """.trimIndent()
                textBodyPart.setText(emailBody, "utf-8", "html") // Set content type to HTML for clickable links

                // Create the attachment part
                val attachmentBodyPart = MimeBodyPart()
                val localFilePath = getPathFromUri(imageUri) // Get the actual file path
                if (localFilePath.isNotEmpty()) {
                    val source = javax.activation.FileDataSource(localFilePath)
                    attachmentBodyPart.dataHandler = javax.activation.DataHandler(source)
                    attachmentBodyPart.fileName = File(localFilePath).name // Or a more descriptive name
                    attachmentBodyPart.disposition = MimeBodyPart.ATTACHMENT
                } else {
                    Log.e("RegisterBottomSheet", "Could not get file path for attachment URI: $imageUri")
                    // Optionally, send email without attachment or handle error
                }


                // Create a multipart message
                val multipart = MimeMultipart()
                multipart.addBodyPart(textBodyPart)
                if (localFilePath.isNotEmpty()) {
                    multipart.addBodyPart(attachmentBodyPart)
                }
                
                // Set the complete message parts
                message.setContent(multipart)

                Transport.send(message)
                Log.d("RegisterBottomSheet", "Officer verification email with attachment sent to $adminEmail for User ID: $officerUserID")

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("RegisterBottomSheet", "Failed to send officer verification email: ${e.message}")
                // Inform the main thread that email sending failed if needed, though registration itself succeeded.
                 withContext(Dispatchers.Main) {
                     Toast.makeText(context, "Officer verification email could not be sent. Please check logs.", Toast.LENGTH_LONG).show()
                 }
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

    suspend fun uploadImageToS3(context: Context, imageUri: Uri, userType: String): String {
        AwsUtils.initialize(context)
        val s3Client = AwsUtils.s3Client
        val bucketName = "navicampbucket"

        val filePath = getPathFromUri(imageUri)
        val file = File(filePath)

        val folder = when (userType) {
            "Security Officer" -> "proof_of_officer"
            "Student", "Personnel", "Visitor" -> "proof_of_general"
            else -> "proof_of_other" // Fallback or handle as an error
        }
        val fileName = "$folder/${System.currentTimeMillis()}_${file.name}"


        val putObjectRequest = PutObjectRequest(bucketName, fileName, file)

        return withContext(Dispatchers.IO) {
            try {
                s3Client.putObject(putObjectRequest)
                fileName
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
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
                updateProofSectionVisibility() // Update visibility after image selection
            }
        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            selectedImageUri = Uri.fromFile(File(currentPhotoPath))
            selectedImageUri?.let {
                selectedImageName.text = File(currentPhotoPath).name
                imageContainer.visibility = View.VISIBLE
                updateProofSectionVisibility() // Update visibility after image capture
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            bottomSheet?.background =
                ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)
        }
        return dialog
    }
    private fun updateProofSectionVisibility() {
        val selectedUserType = userTypeSpinner.selectedItem.toString()

        if (selectedImageUri != null) {
            generalProofLayout.visibility = View.GONE
            officerProofLayout.visibility = View.GONE
            uploadGeneralProofButton.visibility = View.GONE
            uploadProofOfficerButton.visibility = View.GONE
            imageContainer.visibility = View.VISIBLE
            return
        }

        imageContainer.visibility = View.GONE
        when (selectedUserType) {
            "Student", "Personnel", "Visitor" -> {
                generalProofLayout.visibility = View.VISIBLE
                uploadGeneralProofButton.visibility = View.VISIBLE
                officerProofLayout.visibility = View.GONE
                uploadProofOfficerButton.visibility = View.GONE
            }
            "Security Officer" -> {
                generalProofLayout.visibility = View.GONE
                uploadGeneralProofButton.visibility = View.GONE
                officerProofLayout.visibility = View.VISIBLE
                uploadProofOfficerButton.visibility = View.VISIBLE
            }
            else -> { // "User Types" or any other default
                generalProofLayout.visibility = View.GONE
                uploadGeneralProofButton.visibility = View.GONE
                officerProofLayout.visibility = View.GONE
                uploadProofOfficerButton.visibility = View.GONE
            }
        }
    }

    private fun showInfoDialog(proofType: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_info, null)
        val infoText = dialogView.findViewById<TextView>(R.id.info_text) // Assuming R.id.info_text exists in dialog_info.xml

        val message = when (proofType) {
            "general" -> "For Student, Personnel, or Visitor roles, please upload a valid ID or any document that can verify your role (e.g., School ID, Company ID, Event Pass)."
            "officer" -> "For Security Officer role, please upload your Security Officer License or a Certificate of Employment as proof."
            else -> "Please upload the required proof."
        }
        infoText.text = message

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.dialogBackgroundColor)))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.primaryColor))
    }
}