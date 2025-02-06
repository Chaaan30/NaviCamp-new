package com.capstone.navicamp

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.amazonaws.services.s3.model.PutObjectRequest

class LoginBottomSheet : BottomSheetDialogFragment() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var forgotPasswordTextView: TextView

    private val PICK_IMAGE_REQUEST = 1
    private var selectedImageUri: Uri? = null
    private var dialogView: View? = null // Store dialog view reference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_login, container, false)

        emailEditText = view.findViewById(R.id.email)
        passwordEditText = view.findViewById(R.id.password)
        forgotPasswordTextView = view.findViewById(R.id.forgot_password)

        val loginButton = view.findViewById<Button>(R.id.login_button)
        val createAccountButton = view.findViewById<Button>(R.id.create_account_button)

        forgotPasswordTextView.setOnClickListener {
            val forgotPasswordBottomSheet = ForgotPasswordBottomSheet()
            forgotPasswordBottomSheet.show(parentFragmentManager, forgotPasswordBottomSheet.tag)
            dismiss()
        }

        loginButton.setOnClickListener {
            loginUser(view)
        }

        createAccountButton.setOnClickListener {
            val registerBottomSheet = RegisterBottomSheet()
            registerBottomSheet.show(parentFragmentManager, registerBottomSheet.tag)
            dismiss()
        }

        return view
    }

    private fun showLoadingDialog(): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_loading)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        return dialog
    }

    private fun loginUser(view: View) {
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = showLoadingDialog()

        CoroutineScope(Dispatchers.Main).launch {
            val startTime = System.currentTimeMillis()
            try {
                val userData = withContext(Dispatchers.IO) {
                    MySQLHelper.loginUser(email, password)
                }

                if (userData != null) {
                    when (userData.verified) {
                        1 -> {
                            UserSingleton.fullName = userData.fullName
                            val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                            with(sharedPreferences.edit()) {
                                putString("userID", userData.userID)
                                putString("fullName", userData.fullName)
                                putString("userType", userData.userType)
                                putString("email", userData.email)
                                putString("contactNumber", userData.contactNumber)
                                putString("createdOn", userData.createdOn)
                                putString("updatedOn", userData.updatedOn)
                                putBoolean("isLoggedIn", true)
                                apply()
                            }

                            val intent = when (userData.userType) {
                                "Student", "Personnel", "Visitor" -> Intent(context, LocomotorDisabilityActivity::class.java)
                                "Security Officer" -> Intent(context, SecurityOfficerActivity::class.java)
                                else -> null
                            }
                            intent?.let {
                                startActivity(it)
                                dismiss()
                            }
                        }
                        0 -> {
                            Toast.makeText(context, "Your proof of disability is not verified yet.", Toast.LENGTH_SHORT).show()
                        }
                        2 -> {
                            showReuploadProofDialog()
                        }
                    }
                } else {
                    Toast.makeText(context, "Invalid email or password", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("LoginBottomSheet", "Error during login", e)
                Toast.makeText(context, "An error occurred during login", Toast.LENGTH_SHORT).show()
            } finally {
                loadingDialog.dismiss()
                val endTime = System.currentTimeMillis()
                if (endTime - startTime > 5000) {
                    Toast.makeText(context, "Internet is slow, please try again later", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showReuploadProofDialog() {
        CoroutineScope(Dispatchers.Main).launch {
            dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reupload_proof, null)
            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()
            dialog.show()

            val uploadProofButton = dialogView!!.findViewById<Button>(R.id.upload_proof_button)
            val infoButton = dialogView!!.findViewById<ImageButton>(R.id.info_button_login)
            val selectedImageName = dialogView!!.findViewById<TextView>(R.id.selected_image_name)
            val removeImageButton = dialogView!!.findViewById<ImageButton>(R.id.remove_image_button)
            val confirmUploadButton = dialogView!!.findViewById<Button>(R.id.confirm_upload_button)

            uploadProofButton.setOnClickListener {
                openImagePicker()
            }

            infoButton.setOnClickListener {
                val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_info, null)
                val dialog = AlertDialog.Builder(requireContext())
                    .setView(dialogView)
                    .setPositiveButton("OK", null)
                    .create()
                dialog.show()
            }

            removeImageButton.setOnClickListener {
                selectedImageUri = null
                selectedImageName.text = "No file selected"
            }

            confirmUploadButton.setOnClickListener {
                if (selectedImageUri == null) {
                    Toast.makeText(requireContext(), "Please select an image", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                CoroutineScope(Dispatchers.Main).launch {
                    val email = emailEditText.text.toString()

                    val userID = withContext(Dispatchers.IO) {
                        MySQLHelper.getUserIDByEmail(email)?.toInt()
                    }

                    if (userID != null) {
                        val filePath = getPathFromUri(selectedImageUri!!)
                        val file = File(filePath)
                        val proofDisability = "proof_of_disability/${file.name}"

                        val isUpdated = withContext(Dispatchers.IO) {
                            MySQLHelper.updateProofDisability(userID, proofDisability) &&
                                    MySQLHelper.updateUserVerificationStatus(userID.toString(), 0)
                        }

                        if (isUpdated) {
                            try {
                                val uploadedImageName = withContext(Dispatchers.IO) {
                                    uploadImageToS3(requireContext(), selectedImageUri!!)
                                }
                                Toast.makeText(requireContext(), "Image uploaded successfully", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "Image upload failed. Try again.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(requireContext(), "Failed to update proof of disability.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "User not found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
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


    private fun uploadImageAndSaveUri(imageUri: Uri) {
        val loadingDialog = showLoadingDialog()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val uploadedImageName = withContext(Dispatchers.IO) {
                    (activity as RegisterBottomSheet).uploadImageToS3(requireContext(), imageUri)
                }

                val email = emailEditText.text.toString()
                val userData = withContext(Dispatchers.IO) {
                    MySQLHelper.getUserDataByEmail(email)
                }

                val isUpdated = userData?.let {
                    withContext(Dispatchers.IO) {
                        MySQLHelper.updateUserWithUserID(
                            newFullName = it.fullName,
                            newEmail = it.email,
                            newContactNumber = it.contactNumber,
                            userID = it.userID,
                            updatedOn = System.currentTimeMillis().toString()
                        )
                    }
                } ?: false

                if (isUpdated) {
                    withContext(Dispatchers.IO) {
                        MySQLHelper.updateUserVerificationStatus(userData!!.userID, 0)
                    }
                }

                loadingDialog.dismiss()
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Toast.makeText(context, "Image upload failed. Try Again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data
            selectedImageUri?.let {
                val selectedImageName = dialogView!!.findViewById<TextView>(R.id.selected_image_name)
                selectedImageName.text = File(getPathFromUri(it)).name
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            bottomSheet?.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)
        }
        return dialog
    }
}
