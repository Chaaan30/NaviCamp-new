package com.capstone.navicamp

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Locale

class RegisteredUsersFragment : Fragment(R.layout.fragment_registered_users) {

    private lateinit var userTypeSpinner: Spinner
    private lateinit var searchEditText: EditText
    private lateinit var loadingProgress: ProgressBar
    private lateinit var usersLayout: LinearLayout
    private lateinit var userCountText: TextView
    private lateinit var noUsersText: TextView

    private var allUsers = listOf<UserData>()
    private var filteredUsers = listOf<UserData>()
    private var pollingJob: Job? = null
    private val POLLING_INTERVAL = 15000L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize Views
        usersLayout = view.findViewById(R.id.users_layout)
        userTypeSpinner = view.findViewById(R.id.user_type_spinner)
        searchEditText = view.findViewById(R.id.search_edit_text)
        loadingProgress = view.findViewById(R.id.loading_progress)
        userCountText = view.findViewById(R.id.user_count)
        noUsersText = view.findViewById(R.id.no_users_text)

        setupFilters()
        loadUsers()
    }

    private fun setupFilters() {
        val filterOptions = arrayOf("All", "Temporary", "Permanent")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userTypeSpinner.adapter = adapter

        userTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { filterUsers() }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { filterUsers() }
        })
    }

    private fun loadUsers() {
        loadingProgress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            allUsers = withContext(Dispatchers.IO) { MySQLHelper.getAllVerifiedUsers() }
            filteredUsers = allUsers
            updateUserCards()
            loadingProgress.visibility = View.GONE
        }
    }

    private fun filterUsers() {
        val selectedFilter = userTypeSpinner.selectedItem.toString()
        val searchQuery = searchEditText.text.toString().lowercase().trim()

        filteredUsers = allUsers.filter { user ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                else -> user.userType.equals(selectedFilter, ignoreCase = true)
            }

            val matchesSearch = if (searchQuery.isEmpty()) {
                true
            } else {
                user.fullName.lowercase().contains(searchQuery) ||
                        user.userID.lowercase().contains(searchQuery) ||
                        user.email.lowercase().contains(searchQuery) ||
                        user.contactNumber.lowercase().contains(searchQuery)
            }
            matchesFilter && matchesSearch
        }
        updateUserCards()
    }

    private fun updateUserCards() {
        usersLayout.removeAllViews()
        userCountText.text = "Total Users: ${filteredUsers.size}"

        if (filteredUsers.isEmpty()) {
            noUsersText.visibility = View.VISIBLE
        } else {
            noUsersText.visibility = View.GONE
            for (user in filteredUsers) {
                val cardView = layoutInflater.inflate(R.layout.user_card, usersLayout, false)
                setupUserCard(cardView, user)
                usersLayout.addView(cardView)
            }
        }
    }

    private fun setupUserCard(cardView: View, user: UserData) {
        val userIdText = cardView.findViewById<TextView>(R.id.user_id_text)
        val userNameText = cardView.findViewById<TextView>(R.id.user_name_text)
        val userTypeBadge = cardView.findViewById<TextView>(R.id.user_type_badge)
        val emailText = cardView.findViewById<TextView>(R.id.email_text)
        val contactText = cardView.findViewById<TextView>(R.id.contact_text)
        val createdDateText = cardView.findViewById<TextView>(R.id.created_date_text)
        val userTypeIndicator = cardView.findViewById<View>(R.id.user_type_indicator)

        // Set Data
        userIdText.text = "School ID: ${user.userID}"
        userNameText.text = user.fullName
        userTypeBadge.text = user.userType // "Temporary" or "Permanent"
        emailText.text = "Email: ${user.email}"
        contactText.text = "Contact: ${user.contactNumber}"
        createdDateText.text = "Registered: ${formatRegisteredDate(user.createdOn)}"

        // Dynamic Color Logic
        // Temporary = Blue (#0277BD), Permanent = Green (#2E7D32)
        val themeColor = if (user.userType.equals("Temporary", ignoreCase = true)) {
            Color.parseColor("#0277BD") // Material Blue 700
        } else {
            Color.parseColor("#2E7D32") // Material Green 800
        }

        // 1. Set the side indicator color
        userTypeIndicator.setBackgroundColor(themeColor)

        // 2. Set the badge background color (using tint to keep rounded corners)
        userTypeBadge.backgroundTintList = ColorStateList.valueOf(themeColor)

        // 3. Optional: Set the user name color to match the theme for a professional look
        userNameText.setTextColor(themeColor)

        // 4. Set badge text color (White looks better on dark blue/green)
        userTypeBadge.setTextColor(themeColor)
    }

    private fun formatRegisteredDate(rawDateTime: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val output = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault())
            val parsed = input.parse(rawDateTime)
            if (parsed != null) output.format(parsed) else rawDateTime
        } catch (e: Exception) {
            rawDateTime
        }
    }

    private fun startSmartPolling() {
        pollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val newUsers = withContext(Dispatchers.IO) { MySQLHelper.getAllVerifiedUsers() }
                if (newUsers != allUsers) {
                    allUsers = newUsers
                    filterUsers()
                }
                delay(POLLING_INTERVAL)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startSmartPolling()
    }

    override fun onPause() {
        super.onPause()
        pollingJob?.cancel()
    }
}