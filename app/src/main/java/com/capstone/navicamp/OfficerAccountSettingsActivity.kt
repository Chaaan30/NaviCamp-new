package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OfficerAccountSettingsActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_officer_account_settings)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Officer Account Information"

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Initialize navigationView
        navigationView = findViewById(R.id.navigation_view)

        // Retrieve data from SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val fullName = sharedPreferences.getString("fullName", null)
        val userID = sharedPreferences.getString("userID", null)
        val userType = sharedPreferences.getString("userType", null)
        val email = sharedPreferences.getString("email", null)
        val contactNumber = sharedPreferences.getString("contactNumber", null)
        val createdOn = sharedPreferences.getString("createdOn", null)
        val updatedOn = sharedPreferences.getString("updatedOn", null)

        // Log the retrieved values
        Log.d("OfficerAccountSettingsActivity", "fullName: $fullName")
        Log.d("OfficerAccountSettingsActivity", "userID: $userID")
        Log.d("OfficerAccountSettingsActivity", "userType: $userType")
        Log.d("OfficerAccountSettingsActivity", "email: $email")
        Log.d("OfficerAccountSettingsActivity", "contactNumber: $contactNumber")
        Log.d("OfficerAccountSettingsActivity", "createdOn: $createdOn")
        Log.d("OfficerAccountSettingsActivity", "updatedOn: $updatedOn")

        // Format createdOn and updatedOn
        val formattedCreatedOn = createdOn?.takeIf { it.isNotEmpty() }?.let {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(it)
            outputFormat.format(date)
        }

        val formattedUpdatedOn = updatedOn?.takeIf { it.isNotEmpty() }?.let {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(it)
            outputFormat.format(date)
        }

        // Update UI with the retrieved data
        findViewById<TextView>(R.id.full_name_text)?.text = "Full Name: $fullName"
        findViewById<TextView>(R.id.user_id_text)?.text = "User ID: $userID"
        findViewById<TextView>(R.id.user_type_text)?.text = "User Type: $userType"
        findViewById<TextView>(R.id.email_text)?.text = "Email: $email"
        findViewById<TextView>(R.id.contact_number_text)?.text = "Contact Number: $contactNumber"
        findViewById<TextView>(R.id.date_created_text)?.text = "Date Created: $formattedCreatedOn"

        if (formattedUpdatedOn != null) {
            findViewById<TextView>(R.id.updated_on_text)?.text = "Updated On: $formattedUpdatedOn"
            findViewById<View>(R.id.updated_on_card)?.visibility = View.VISIBLE
        }

        // Set up NavigationView item click listener
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    // Clear SharedPreferences
                    val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.clear()
                    editor.apply()

                    // Navigate to MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_item1 -> {
                    // Navigate to OfficerAccountSettingsActivity
                    val intent = Intent(this, OfficerAccountSettingsActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_item2 -> {
                    // Navigate to SecurityOfficerActivity and clear the activity stack
                    val intent = Intent(this, SecurityOfficerActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }

        // Set up Update Info button click listener
        val updateInfoButton: Button = findViewById(R.id.update_info_button)
        val cancelEditButton: Button = findViewById(R.id.cancel_edit_button)
        val editFullName: EditText = findViewById(R.id.edit_full_name)
        val editEmail: EditText = findViewById(R.id.edit_email)
        val editContactNumber: EditText = findViewById(R.id.edit_contact_number)

        updateInfoButton.setOnClickListener {
            if (editFullName.visibility == View.VISIBLE) {
                val newFullName = editFullName.text.toString()
                val newEmail = editEmail.text.toString()
                val newContactNumber = editContactNumber.text.toString()
                val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                CoroutineScope(Dispatchers.Main).launch {
                    val userId = userID ?: return@launch // Handle null userID case
                    if (newEmail.isNotBlank() && MySQLHelper.isEmailExists(newEmail, userId)) {
                        Toast.makeText(this@OfficerAccountSettingsActivity, "Email already exists. Please use a different email.", Toast.LENGTH_SHORT).show()
                    } else if (newContactNumber.isNotBlank() && MySQLHelper.isContactNumberExists(newContactNumber, userId)) {
                        Toast.makeText(this@OfficerAccountSettingsActivity, "Contact number already exists. Please use a different contact number.", Toast.LENGTH_SHORT).show()
                    } else {
                        val success = MySQLHelper.updateUserWithUserID(newFullName, newEmail, newContactNumber, userId, currentDateTime)
                        if (success) {
                            // Update SharedPreferences and UI
                            val editor = sharedPreferences.edit()
                            if (newFullName.isNotBlank()) {
                                editor.putString("fullName", newFullName)
                                findViewById<TextView>(R.id.full_name_text)?.text = "Full Name: $newFullName"
                                // Update the header view
                                val headerView = navigationView.getHeaderView(0)
                                headerView.findViewById<TextView>(R.id.nav_name_header)?.text = newFullName
                                // Update UserSingleton
                                UserSingleton.fullName = newFullName
                            }
                            if (newEmail.isNotBlank()) {
                                editor.putString("email", newEmail)
                                findViewById<TextView>(R.id.email_text)?.text = "Email: $newEmail"
                            } else {
                                findViewById<TextView>(R.id.email_text)?.text = "Email: $email"
                            }
                            if (newContactNumber.isNotBlank()) {
                                editor.putString("contactNumber", newContactNumber)
                                findViewById<TextView>(R.id.contact_number_text)?.text = "Contact Number: $newContactNumber"
                            } else {
                                findViewById<TextView>(R.id.contact_number_text)?.text = "Contact Number: $contactNumber"
                            }
                            editor.putString("updatedOn", currentDateTime)
                            editor.apply()

                            findViewById<TextView>(R.id.updated_on_text)?.text = "Updated On: ${SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date())}"
                            findViewById<View>(R.id.updated_on_card)?.visibility = View.VISIBLE

                            // Show toast message
                            Toast.makeText(this@OfficerAccountSettingsActivity, "Information updated successfully", Toast.LENGTH_SHORT).show()

                            // Clear EditText fields
                            editFullName.text.clear()
                            editEmail.text.clear()
                            editContactNumber.text.clear()

                            editFullName.visibility = View.GONE
                            editEmail.visibility = View.GONE
                            editContactNumber.visibility = View.GONE
                            cancelEditButton.visibility = View.GONE
                        }
                    }
                }
            } else {
                editFullName.visibility = View.VISIBLE
                editEmail.visibility = View.VISIBLE
                editContactNumber.visibility = View.VISIBLE
                cancelEditButton.visibility = View.VISIBLE
            }
        }

        cancelEditButton.setOnClickListener {
            editFullName.visibility = View.GONE
            editEmail.visibility = View.GONE
            editContactNumber.visibility = View.GONE
            cancelEditButton.visibility = View.GONE
        }
    }
}